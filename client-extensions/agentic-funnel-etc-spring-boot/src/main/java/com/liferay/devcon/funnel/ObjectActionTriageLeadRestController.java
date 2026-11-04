package com.liferay.devcon.funnel;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;

import java.util.Map;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author José Abelenda
 */
@RequestMapping("/object/action/triage-lead")
@RestController
public class ObjectActionTriageLeadRestController extends BaseRestController {

	public ObjectActionTriageLeadRestController(
		AIHubAgentService aiHubAgentService,
		@Value("${com.liferay.devcon.funnel.agent.classifier.external.reference.code}")
			String classifierExternalReferenceCode,
		@Value("${com.liferay.devcon.funnel.icp.definition}") String
			icpDefinition,
		LeadService leadService,
		@Value("${com.liferay.lxc.dxp.mainDomain}") String lxcDXPMainDomain,
		@Value("${com.liferay.devcon.funnel.min.fit.score}") int minFitScore,
		@Value("${com.liferay.devcon.funnel.agent.outreach.external.reference.code}")
			String outreachExternalReferenceCode,
		@Value("${com.liferay.devcon.funnel.sender.profile}") String
			senderProfile,
		@Qualifier("triageTaskExecutor") TaskExecutor triageTaskExecutor) {

		_aiHubAgentService = aiHubAgentService;
		_leadService = leadService;
		_triageTaskExecutor = triageTaskExecutor;
		_classifierExternalReferenceCode = classifierExternalReferenceCode;
		_icpDefinition = icpDefinition;
		_lxcDXPMainDomain = lxcDXPMainDomain;
		_minFitScore = minFitScore;
		_outreachExternalReferenceCode = outreachExternalReferenceCode;
		_senderProfile = senderProfile;
	}

	@PostMapping
	public ResponseEntity<String> post(@RequestBody String json) {
		JSONObject jsonObject = new JSONObject(json);

		JSONObject objectEntryJSONObject = jsonObject.getJSONObject(
			"objectEntry");

		long objectEntryId = objectEntryJSONObject.getLong("objectEntryId");

		JSONObject valuesJSONObject = objectEntryJSONObject.getJSONObject(
			"values");

		String triageStatus = valuesJSONObject.optString("triageStatus", null);

		if (!Objects.equals(triageStatus, TriageStatus.NEW)) {
			if (_log.isInfoEnabled()) {
				_log.info(
					"Skipping lead " + objectEntryId + " in triage status " +
						triageStatus);
			}

			return ResponseEntity.ok(
				new JSONObject(
				).put(
					"skipped", true
				).put(
					"triageStatus", String.valueOf(triageStatus)
				).toString());
		}

		try {

			_leadService.patch(
				objectEntryId,
				new JSONObject(
				).put(
					"triageStatus", TriageStatus.TRIAGING
				));

			_triageTaskExecutor.execute(
				() -> _triageOrFail(objectEntryId, valuesJSONObject));

			return ResponseEntity.accepted(
			).body(
				new JSONObject(
				).put(
					"triageStatus", TriageStatus.TRIAGING
				).toString()
			);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to start triage for lead " + objectEntryId, exception);

			_leadService.patchFailedQuietly(objectEntryId, exception);

			return ResponseEntity.internalServerError(
			).body(
				new JSONObject(
				).put(
					"error", String.valueOf(exception.getMessage())
				).toString()
			);
		}
	}

	private String _getLeadJSON(JSONObject valuesJSONObject) {
		return new JSONObject(
		).put(
			"companyName", valuesJSONObject.optString("companyName")
		).put(
			"contactEmail", valuesJSONObject.optString("contactEmail")
		).put(
			"contactName", valuesJSONObject.optString("contactName")
		).put(
			"employeeCount", valuesJSONObject.opt("employeeCount")
		).put(
			"industry", valuesJSONObject.optString("industry")
		).put(
			"rawNotes", valuesJSONObject.optString("rawNotes")
		).put(
			"sourceChannel", valuesJSONObject.optString("sourceChannel")
		).toString();
	}

	private String _getLeadReference(long objectEntryId) {
		return _lxcDXPMainDomain + "/DEVCON_LEAD/" + objectEntryId;
	}

	private String _toSegmentKey(String segment) {
		if (Objects.equals(segment, "mid-market")) {
			return "midMarket";
		}

		return segment;
	}

	private void _triage(long objectEntryId, JSONObject valuesJSONObject) {
		String leadJSON = _getLeadJSON(valuesJSONObject);
		String leadReference = _getLeadReference(objectEntryId);

		JSONObject classificationJSONObject = _aiHubAgentService.invoke(
			_classifierExternalReferenceCode,
			Map.of(
				"icpDefinition", _icpDefinition, "leadJSON", leadJSON,
				"leadReference", leadReference));

		int score = classificationJSONObject.optInt("score");
		String segment = classificationJSONObject.getString("segment");

		JSONObject patchJSONObject = new JSONObject(
		).put(
			"classificationRationale",
			classificationJSONObject.optString("rationale")
		).put(
			"confidence", classificationJSONObject.optString("confidence")
		).put(
			"fitScore", score
		).put(
			"segment", _toSegmentKey(segment)
		);

		if (Objects.equals(segment, "disqualified")) {
			patchJSONObject.put(
				"reviewNotes",
				"Disqualified by the classifier. No outreach was drafted and " +
					"no channel was opened."
			).put(
				"triageStatus", TriageStatus.REJECTED
			);

			_leadService.patch(objectEntryId, patchJSONObject);

			return;
		}

		if (score < _minFitScore) {
			patchJSONObject.put(
				"reviewNotes",
				"Fit score " + score + " is below the minimum of " +
					_minFitScore +
						". No outreach was drafted and no channel was opened."
			).put(
				"triageStatus", TriageStatus.REJECTED
			);

			_leadService.patch(objectEntryId, patchJSONObject);

			return;
		}

		JSONObject outreachJSONObject = _aiHubAgentService.invoke(
			_outreachExternalReferenceCode,
			Map.of(
				"classificationJSON", classificationJSONObject.toString(),
				"leadJSON", leadJSON, "leadReference", leadReference,
				"senderProfile", _senderProfile));

		patchJSONObject.put(
			"outreachBody", outreachJSONObject.optString("body")
		).put(
			"outreachSubject", outreachJSONObject.optString("subject")
		).put(
			"reviewNotes", outreachJSONObject.optString("reviewNotes")
		).put(
			"triageStatus",
			outreachJSONObject.optBoolean("shouldSend", false) ?
				TriageStatus.QUALIFIED : TriageStatus.REJECTED
		);

		_leadService.patch(objectEntryId, patchJSONObject);
	}

	private void _triageOrFail(long objectEntryId, JSONObject valuesJSONObject) {
		try {
			_triage(objectEntryId, valuesJSONObject);

			if (_log.isInfoEnabled()) {
				_log.info("Triaged lead " + objectEntryId);
			}
		}
		catch (Exception exception) {
			_log.error("Unable to triage lead " + objectEntryId, exception);

			_leadService.patchFailedQuietly(objectEntryId, exception);
		}
	}

	private static final Log _log = LogFactory.getLog(
		ObjectActionTriageLeadRestController.class);

	private final AIHubAgentService _aiHubAgentService;

	private final String _classifierExternalReferenceCode;

	private final String _icpDefinition;

	private final LeadService _leadService;

	private final String _lxcDXPMainDomain;

	private final int _minFitScore;

	private final String _outreachExternalReferenceCode;

	private final String _senderProfile;

	private final TaskExecutor _triageTaskExecutor;

}
