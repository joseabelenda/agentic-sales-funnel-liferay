package com.liferay.devcon.funnel;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author José Abelenda
 */
@RequestMapping("/ready")
@RestController
public class ReadyRestController {

	@GetMapping
	public ResponseEntity<String> get() {
		return ResponseEntity.ok("{\"status\": \"ready\"}");
	}

}
