package com.example.springbootgrafana;

import org.testcontainers.grafana.LgtmStackContainer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	LgtmStackContainer lgtmContainer() {
		return new LgtmStackContainer("grafana/otel-lgtm:0.11.8");
	}

}
