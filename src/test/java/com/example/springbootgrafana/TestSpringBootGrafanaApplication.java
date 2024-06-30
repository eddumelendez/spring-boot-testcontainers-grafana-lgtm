package com.example.springbootgrafana;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;

@TestConfiguration(proxyBeanMethods = false)
public class TestSpringBootGrafanaApplication {

	public static void main(String[] args) {
		SpringApplication.from(SpringBootGrafanaApplication::main).with(TestSpringBootGrafanaApplication.class).run(args);
	}

	@Bean
//	@ServiceConnection("otel/opentelemetry-collector-contrib")
	LgtmStackContainer lgtmContainer(DynamicPropertyRegistry registry) {
		LgtmStackContainer lgtm = new LgtmStackContainer();
		registry.add("management.otlp.metrics.export.url", () -> "http://%s:%d/v1/metrics".formatted(lgtm.getHost(), lgtm.getMappedPort(4318)));
		registry.add("management.otlp.tracing.endpoint", () -> "http://%s:%d/v1/traces".formatted(lgtm.getHost(), lgtm.getMappedPort(4318)));
		registry.add("management.otlp.logging.endpoint", () -> "http://%s:%d/v1/logs".formatted(lgtm.getHost(), lgtm.getMappedPort(4318)));
		return lgtm;
	}

	static class LgtmStackContainer extends GenericContainer<LgtmStackContainer> {

		LgtmStackContainer() {
			super("grafana/otel-lgtm:0.6.0");
			withExposedPorts(3000, 4317, 4318);
			// workaround to map service.name to service_name
			withCopyFileToContainer(MountableFile.forClasspathResource("grafana-datasources.yaml"), "/otel-lgtm/grafana-v11.0.0/conf/provisioning/datasources/grafana-datasources.yaml");
			withEnv("OTEL_METRIC_EXPORT_INTERVAL", "500");
			waitingFor(Wait.forLogMessage(".*The OpenTelemetry collector and the Grafana LGTM stack are up and running.*\\s", 1));
			withStartupTimeout(Duration.ofMinutes(2));
		}

	}

//	@Bean
//	LogRecordProcessor simpleLogRecordProcessor(ObjectProvider<LogRecordExporter> logRecordExporters) {
//		return SimpleLogRecordProcessor.create(LogRecordExporter.composite(logRecordExporters.orderedStream().toList()));
//	}

//	@Bean
//	LoggingSpanExporter logRecordExporter() {
//		return LoggingSpanExporter.create();
//	}

}
