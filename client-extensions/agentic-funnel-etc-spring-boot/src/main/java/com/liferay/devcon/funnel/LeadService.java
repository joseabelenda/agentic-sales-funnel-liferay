package com.liferay.devcon.funnel;

import com.liferay.client.extension.util.spring.boot3.client.LiferayOAuth2AccessTokenManager;
import com.liferay.client.extension.util.spring.boot3.service.BaseService;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author José Abelenda
 */
@Service
public class LeadService extends BaseService {

	public JSONArray getObjectEntries(String filter) {
		String pageJSON = get(
			_liferayOAuth2AccessTokenManager.getAuthorization(
				_OAUTH2_HEADLESS_SERVER_EXTERNAL_REFERENCE_CODE),
			UriComponentsBuilder.fromPath(
				_leadObjectRESTPath
			).queryParam(
				"fields", "dateModified,id"
			).queryParam(
				"filter", filter
			).queryParam(
				"pageSize", 100
			).build(
			).encode(
			).toUri());

		return new JSONObject(
			pageJSON
		).getJSONArray(
			"items"
		);
	}

	public void patch(long objectEntryId, JSONObject valuesJSONObject) {
		patch(
			_liferayOAuth2AccessTokenManager.getAuthorization(
				_OAUTH2_HEADLESS_SERVER_EXTERNAL_REFERENCE_CODE),
			valuesJSONObject.toString(),
			URI.create(_leadObjectRESTPath + "/" + objectEntryId));
	}

	public void patchFailed(long objectEntryId, Exception exception) {
		patch(
			objectEntryId,
			new JSONObject(
			).put(
				"errorMessage", String.valueOf(exception.getMessage())
			).put(
				"triageStatus", TriageStatus.FAILED
			));
	}

	public void patchFailedQuietly(long objectEntryId, Exception exception) {
		try {
			patchFailed(objectEntryId, exception);
		}
		catch (Exception patchException) {
			exception.addSuppressed(patchException);

			_log.error(
				"Unable to record the failure on lead " + objectEntryId +
					"; the lead is still in its previous status",
				exception);
		}
	}

	private static final String
		_OAUTH2_HEADLESS_SERVER_EXTERNAL_REFERENCE_CODE =
			"agentic-funnel-etc-spring-boot-oahs";

	private static final Log _log = LogFactory.getLog(LeadService.class);

	@Value("${com.liferay.devcon.funnel.lead.object.rest.path}")
	private String _leadObjectRESTPath;

	@Autowired
	private LiferayOAuth2AccessTokenManager _liferayOAuth2AccessTokenManager;

}
