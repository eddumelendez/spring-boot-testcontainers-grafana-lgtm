package com.example.springbootgrafana;

import java.time.Duration;

import com.example.springbootgrafana.SpringBootGrafanaApplicationTests.TestAfterAllCallback;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "management.otlp.metrics.export.step=1s", "management.tracing.sampling.probability=1" })
@ExtendWith(TestAfterAllCallback.class)
@AutoConfigureObservability
class SpringBootGrafanaApplicationTests {

	@LocalServerPort
	private int localPort;

	@Autowired
	private LgtmStackContainer lgtmStack;

	@Test
	void contextLoads() {
		RestAssured.given().port(this.localPort).get("/greetings").then().assertThat().statusCode(200);

		var serviceAccountKey = getServiceAccountKey(this.lgtmStack);
		var authHeader = "Bearer " + serviceAccountKey;

		Awaitility.given()
			.pollInterval(Duration.ofSeconds(2))
			.atMost(Duration.ofSeconds(15))
			.ignoreExceptions()
			.untilAsserted(() -> RestAssured.given()
				.baseUri(this.lgtmStack.getGrafanaHttpUrl())
				.basePath("/api/datasources/proxy/uid/prometheus")
				.header("Authorization", authHeader)
				.queryParam("query", "http_server_requests_milliseconds_count{uri=\"/greetings\"}")
				.get("/api/v1/query")
				.prettyPeek()
				.then()
				.assertThat()
				.statusCode(200)
				.body("data.result[0].value", Matchers.hasItem("1")));

		Awaitility.given()
			.pollInterval(Duration.ofSeconds(2))
			.atMost(Duration.ofSeconds(15))
			.ignoreExceptions()
			.untilAsserted(() -> RestAssured.given()
				.baseUri(this.lgtmStack.getGrafanaHttpUrl())
				.basePath("/api/datasources/proxy/uid/tempo")
				.header("Authorization", authHeader)
				.queryParam("q", "{resource.service.name=\"TestSpringBootDemo\"}")
				.get("/api/search")
				.prettyPeek()
				.then()
				.assertThat()
				.statusCode(200)
				.body("traces[0].spanSets.spans", Matchers.hasSize(1))
				.and()
				.body("traces[0].rootTraceName", Matchers.equalTo("http get /greetings")));

		Awaitility.given()
			.pollInterval(Duration.ofSeconds(2))
			.atMost(Duration.ofSeconds(15))
			.ignoreExceptions()
			.untilAsserted(() -> RestAssured.given()
				.baseUri(this.lgtmStack.getGrafanaHttpUrl())
				.basePath("/api/datasources/proxy/uid/loki")
				.header("Authorization", authHeader)
				.queryParam("query", "{service_name=\"TestSpringBootDemo\"} | foo=`bar` |= ``")
				.get("/loki/api/v1/query_range")
				.prettyPeek()
				.then()
				.assertThat()
				.statusCode(200)
				.body("data.result[0].values[0]", Matchers.hasItem("request...")));
	}

	private static Object getServiceAccountKey(LgtmStackContainer lgtmStack) {
		var serviceAccountId = RestAssured.given()
			.baseUri(lgtmStack.getGrafanaHttpUrl())
			.auth()
			.preemptive()
			.basic("admin", "admin")
			.contentType(ContentType.JSON)
			.accept(ContentType.JSON)
			.body("""
					{
					  "name": "grafana",
					  "role": "Viewer",
					  "isDisabled": false
					}
					""")
			.post("/api/serviceaccounts")
			.prettyPeek()
			.body()
			.jsonPath()
			.get("id");

		var serviceAccountKey = RestAssured.given()
			.baseUri(lgtmStack.getGrafanaHttpUrl())
			.auth()
			.preemptive()
			.basic("admin", "admin")
			.contentType(ContentType.JSON)
			.accept(ContentType.JSON)
			.body("""
					{
						"name": "grafana"
					}
					""")
			.post("/api/serviceaccounts/{id}/tokens ", serviceAccountId)
			.prettyPeek()
			.body()
			.jsonPath()
			.get("key");
		return serviceAccountKey;
	}

	static class TestAfterAllCallback implements AfterAllCallback {

		@Override
		public void afterAll(ExtensionContext context) {
			ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
			OtlpMeterRegistry meterRegistry = applicationContext.getBean(OtlpMeterRegistry.class);
			meterRegistry.close();
		}

	}

}
