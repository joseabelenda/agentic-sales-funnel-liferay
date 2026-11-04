package com.liferay.devcon.funnel;

/**
 * @author José Abelenda
 */
public class AIHubURLUtil {

	public static String normalize(String aiHubURL) {
		String normalizedAIHubURL = aiHubURL.strip();

		while (normalizedAIHubURL.endsWith("/")) {
			normalizedAIHubURL = normalizedAIHubURL.substring(
				0, normalizedAIHubURL.length() - 1);
		}

		if (!normalizedAIHubURL.startsWith("https://") &&
			!normalizedAIHubURL.startsWith("http://")) {

			throw new IllegalArgumentException(
				"The AI Hub URL must be absolute: " + aiHubURL);
		}

		return normalizedAIHubURL;
	}

}
