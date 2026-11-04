package com.liferay.devcon.funnel;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;

import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONObject;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author José Abelenda
 */
@RequestMapping("/object/action/open-channel")
@RestController
public class ObjectActionOpenChannelRestController
	extends BaseRestController {

	public ObjectActionOpenChannelRestController(
		ChannelService channelService, LeadService leadService) {

		_channelService = channelService;
		_leadService = leadService;
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

		if (!Objects.equals(triageStatus, TriageStatus.QUALIFIED)) {
			return ResponseEntity.ok(
				new JSONObject(
				).put(
					"skipped", true
				).put(
					"triageStatus", String.valueOf(triageStatus)
				).toString());
		}

		String channelURL = valuesJSONObject.optString("channelURL", null);

		if ((channelURL != null) && !channelURL.isBlank()) {
			if (_log.isInfoEnabled()) {
				_log.info(
					"Channel already open for lead " + objectEntryId + " at " +
						channelURL);
			}

			return ResponseEntity.ok(
				new JSONObject(
				).put(
					"channelURL", channelURL
				).put(
					"skipped", true
				).toString());
		}

		try {
			String openedChannelURL = _channelService.openChannel(
				objectEntryId, valuesJSONObject.getString("outreachSubject"),
				valuesJSONObject.getString("outreachBody"));

			JSONObject patchJSONObject = new JSONObject(
			).put(
				"channelURL", openedChannelURL
			).put(
				"triageStatus", TriageStatus.CHANNEL_OPENED
			);

			_leadService.patch(objectEntryId, patchJSONObject);

			return ResponseEntity.ok(patchJSONObject.toString());
		}
		catch (Exception exception) {
			_log.error(
				"Unable to open a channel for lead " + objectEntryId,
				exception);

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

	private static final Log _log = LogFactory.getLog(
		ObjectActionOpenChannelRestController.class);

	private final ChannelService _channelService;

	private final LeadService _leadService;

}
