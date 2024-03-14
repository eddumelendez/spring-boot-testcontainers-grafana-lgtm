package com.example.springbootgrafana;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestSpringBootGrafanaApplication {

	public static void main(String[] args) {
		SpringApplication.from(SpringBootGrafanaApplication::main).with(TestSpringBootGrafanaApplication.class).run(args);
	}

	@Bean
	@ServiceConnection("otel/opentelemetry-collector-contrib")
	GenericContainer<?> lgtmContainer() {
		GenericContainer<?> lgtm = new GenericContainer<>("grafana/otel-lgtm:0.4.0")
				// grafana, otel grpc, otel http
				.withExposedPorts(3000, 4317, 4318)
				.withEnv("OTEL_METRIC_EXPORT_INTERVAL", "500")
				.waitingFor(Wait.forLogMessage(".*The OpenTelemetry collector and the Grafana LGTM stack are up and running.*\\s", 1))
				.withStartupTimeout(Duration.ofMinutes(2));
		return lgtm;
	}

}
