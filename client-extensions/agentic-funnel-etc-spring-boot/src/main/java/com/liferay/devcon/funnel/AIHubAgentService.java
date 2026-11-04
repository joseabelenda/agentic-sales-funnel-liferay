package com.liferay.devcon.funnel;

import com.liferay.client.extension.util.spring.boot3.service.BaseService;

import java.net.URI;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * @author José Abelenda
 */
@Service
public class AIHubAgentService extends BaseService {

	public AIHubAgentService(
		AIHubAccessTokenManager aiHubAccessTokenManager,
		@Value("${com.liferay.devcon.funnel.ai.hub.url}") String aiHubURL) {

		_aiHubAccessTokenManager = aiHubAccessTokenManager;

		_agentInstancesURI = URI.create(
			AIHubURLUtil.normalize(aiHubURL) +
				"/o/ai-hub/v1.0/agent-instances");
	}

	public JSONObject invoke(
		String agentDefinitionExternalReferenceCode,
		Map<String, String> context) {

		JSONObject contextJSONObject = new JSONObject();

		context.forEach(contextJSONObject::put);

		String body = new JSONObject(
		).put(
			"agentDefinitionExternalReferenceCode",
			agentDefinitionExternalReferenceCode
		).put(
			"asynchronous", false
		).put(
			"context", contextJSONObject
		).toString();

		String agentInstanceJSON = null;

		try {
			agentInstanceJSON = post(
				_aiHubAccessTokenManager.getAuthorization(), body,
				_agentInstancesURI);
		}
		catch (WebClientResponseException webClientResponseException) {
			if (webClientResponseException.getStatusCode(
				).value() != HttpStatus.UNAUTHORIZED.value()) {

				throw webClientResponseException;
			}

			if (_log.isWarnEnabled()) {
				_log.warn(
					"AI Hub rejected the access token; fetching a new one " +
						"and retrying once");
			}

			_aiHubAccessTokenManager.invalidate();

			agentInstanceJSON = post(
				_aiHubAccessTokenManager.getAuthorization(), body,
				_agentInstancesURI);
		}

		return AgentOutputParser.parseOutput(
			agentDefinitionExternalReferenceCode, agentInstanceJSON);
	}

	private static final Log _log = LogFactory.getLog(
		AIHubAgentService.class);

	private final URI _agentInstancesURI;

	private final AIHubAccessTokenManager _aiHubAccessTokenManager;

}
