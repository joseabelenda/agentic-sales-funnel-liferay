package com.liferay.devcon.funnel;

import java.time.Instant;

import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * @author José Abelenda
 */
@Component
public class AIHubAccessTokenManager {

	public AIHubAccessTokenManager(
		@Value("${com.liferay.devcon.funnel.ai.hub.url}") String aiHubURL,
		@Value("${com.liferay.devcon.funnel.ai.hub.client.id}") String clientId,
		@Value("${com.liferay.devcon.funnel.ai.hub.client.secret}") String
			clientSecret) {

		_clientId = clientId;
		_clientSecret = clientSecret;

		_tokenURL = AIHubURLUtil.normalize(aiHubURL) + "/o/oauth2/token";
	}

	public synchronized String getAuthorization() {
		Instant now = Instant.now();

		if ((_accessToken == null) || now.isAfter(_refreshAfter)) {
			_fetchAccessToken(now);
		}

		return "Bearer " + _accessToken;
	}

	public synchronized void invalidate() {
		_accessToken = null;
	}

	private void _fetchAccessToken(Instant now) {
		MultiValueMap<String, String> formMultiValueMap =
			new LinkedMultiValueMap<>();

		formMultiValueMap.add("client_id", _clientId);
		formMultiValueMap.add("client_secret", _clientSecret);
		formMultiValueMap.add("grant_type", "client_credentials");

		JSONObject jsonObject = new JSONObject(
			_restClient.post(
			).uri(
				_tokenURL
			).contentType(
				MediaType.APPLICATION_FORM_URLENCODED
			).body(
				formMultiValueMap
			).retrieve(
			).body(
				String.class
			));

		_accessToken = jsonObject.getString("access_token");

		_refreshAfter = now.plusSeconds(
			jsonObject.optLong("expires_in", 600) - 60);
	}

	private String _accessToken;

	private final String _clientId;

	private final String _clientSecret;

	private Instant _refreshAfter = Instant.EPOCH;

	private final RestClient _restClient = RestClient.create();

	private final String _tokenURL;

}
