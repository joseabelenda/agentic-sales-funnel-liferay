package com.liferay.devcon.funnel;

import java.time.Duration;
import java.time.Instant;

import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author José Abelenda
 */
@Component
public class StaleTriageSweeper {

	public StaleTriageSweeper(
		LeadService leadService,
		@Value("${com.liferay.devcon.funnel.triage.stale.minutes}") long
			staleMinutes) {

		_leadService = leadService;
		_staleDuration = Duration.ofMinutes(staleMinutes);
	}

	@Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
	public void failStaleTriages() {
		try {
			_failStaleTriages();
		}
		catch (Exception exception) {
			_log.error("Unable to sweep stale triages", exception);
		}
	}

	private void _failStaleTriages() {
		Instant deadline = Instant.now(
		).minus(
			_staleDuration
		);

		JSONArray itemsJSONArray = _leadService.getObjectEntries(
			"triageStatus eq '" + TriageStatus.TRIAGING + "'");

		for (int i = 0; i < itemsJSONArray.length(); i++) {
			JSONObject itemJSONObject = itemsJSONArray.getJSONObject(i);

			Instant dateModified = Instant.parse(
				itemJSONObject.getString("dateModified"));

			if (dateModified.isAfter(deadline)) {
				continue;
			}

			long objectEntryId = itemJSONObject.getLong("id");

			_log.warn(
				"Lead " + objectEntryId + " has been triaging since " +
					dateModified + "; marking it as failed");

			_leadService.patchFailedQuietly(
				objectEntryId,
				new IllegalStateException(
					"Triage did not complete within " +
						_staleDuration.toMinutes() +
							" minutes. Retry the Triage Lead action."));
		}
	}

	private static final Log _log = LogFactory.getLog(
		StaleTriageSweeper.class);

	private final LeadService _leadService;

	private final Duration _staleDuration;

}
