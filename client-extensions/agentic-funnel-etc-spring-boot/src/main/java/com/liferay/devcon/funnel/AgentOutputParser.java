package com.liferay.devcon.funnel;

import org.json.JSONObject;

/**
 * @author José Abelenda
 */
public class AgentOutputParser {

	public static JSONObject parseOutput(
		String agentDefinitionExternalReferenceCode, String agentInstanceJSON) {

		JSONObject agentInstanceJSONObject = new JSONObject(agentInstanceJSON);

		String output = agentInstanceJSONObject.optString("output", null);

		if ((output == null) || output.isBlank() || output.equals("null")) {
			throw new IllegalStateException(
				"AI Hub returned no output for agent " +
					agentDefinitionExternalReferenceCode +
						". Check the token balance of the AI Hub account " +
							"before retrying.");
		}

		return new JSONObject(_unfence(output));
	}

	private static String _unfence(String output) {
		String trimmedOutput = output.strip();

		if (!trimmedOutput.startsWith("```")) {
			return trimmedOutput;
		}

		int beginIndex = trimmedOutput.indexOf('\n');

		if (beginIndex == -1) {
			return trimmedOutput;
		}

		int endIndex = trimmedOutput.lastIndexOf("```");

		if (endIndex <= beginIndex) {
			return trimmedOutput.substring(beginIndex + 1);
		}

		return trimmedOutput.substring(beginIndex + 1, endIndex);
	}

}
