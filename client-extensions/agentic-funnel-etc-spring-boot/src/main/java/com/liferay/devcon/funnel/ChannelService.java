package com.liferay.devcon.funnel;

import com.liferay.client.extension.util.spring.boot3.client.LiferayOAuth2AccessTokenManager;
import com.liferay.client.extension.util.spring.boot3.service.BaseService;

import java.net.URI;

import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author José Abelenda
 */
@Service
public class ChannelService extends BaseService {

	public String openChannel(long objectEntryId, String subject, String body) {
		String channelPath =
			_channelObjectRESTPath + "/by-external-reference-code/" +
				_getChannelExternalReferenceCode(objectEntryId);

		put(
			_liferayOAuth2AccessTokenManager.getAuthorization(
				_OAUTH2_HEADLESS_SERVER_EXTERNAL_REFERENCE_CODE),
			new JSONObject(
			).put(
				"body", body
			).put(
				"r_leadChannels_c_devconLeadId", objectEntryId
			).put(
				"subject", subject
			).toString(),
			URI.create(channelPath));

		return lxcDXPServerProtocol + "://" + lxcDXPMainDomain + channelPath;
	}

	private String _getChannelExternalReferenceCode(long objectEntryId) {
		return "DEVCON_LEAD_CHANNEL_" + objectEntryId;
	}

	private static final String
		_OAUTH2_HEADLESS_SERVER_EXTERNAL_REFERENCE_CODE =
			"agentic-funnel-etc-spring-boot-oahs";

	@Value("${com.liferay.devcon.funnel.channel.object.rest.path}")
	private String _channelObjectRESTPath;

	@Autowired
	private LiferayOAuth2AccessTokenManager _liferayOAuth2AccessTokenManager;

}
