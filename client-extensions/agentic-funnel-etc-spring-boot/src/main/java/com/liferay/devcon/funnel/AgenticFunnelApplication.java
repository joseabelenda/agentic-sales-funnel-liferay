package com.liferay.devcon.funnel;

import com.liferay.client.extension.util.spring.boot3.ClientExtensionUtilSpringBootComponentScan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * @author José Abelenda
 */
@Import(ClientExtensionUtilSpringBootComponentScan.class)
@SpringBootApplication
public class AgenticFunnelApplication {

	public static void main(String[] arguments) {
		SpringApplication.run(AgenticFunnelApplication.class, arguments);
	}

}
