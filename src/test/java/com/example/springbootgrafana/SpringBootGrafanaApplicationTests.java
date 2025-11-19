package com.example.springbootgrafana;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import net.minidev.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "management.otlp.metrics.export.step=1s", "management.tracing.sampling.probability=1" })
@AutoConfigureRestTestClient
@AutoConfigureMetrics
@AutoConfigureTracing
class SpringBootGrafanaApplicationTests {

	@Autowired
	private RestTestClient restTestClient;

	@Autowired
	private LgtmStackContainer lgtmStack;

	@Test
	void contextLoads() {
		this.restTestClient.get().uri("/greetings").exchangeSuccessfully();

		var lgtmRestTestClient = RestTestClient.bindToServer().baseUrl(lgtmStack.getGrafanaHttpUrl()).build();

		var serviceAccountKey = getServiceAccountKey(lgtmRestTestClient);
		var authHeader = "Bearer " + serviceAccountKey;

		Awaitility.given()
			.pollInterval(Duration.ofSeconds(2))
			.atMost(Duration.ofSeconds(15))
			.ignoreExceptions()
			.untilAsserted(() -> lgtmRestTestClient.post()
				.uri(UriComponentsBuilder.fromPath("/api/datasources/proxy/uid/prometheus/api/v1/query")
					.queryParam("query", "http_server_request_duration_milliseconds_count{http_route=\"/greetings\"}")
					.build()
					.toUri())
				.header(HttpHeaders.AUTHORIZATION, authHeader)
				.exchange()
				.expectBody()
				.jsonPath("data.result[0].value")
				.value(JSONArray.class, value -> assertThat(value).contains("1")));

		Awaitility.given()
			.pollInterval(Duration.ofSeconds(2))
			.atMost(Duration.ofSeconds(15))
			.ignoreExceptions()
			.untilAsserted(() -> lgtmRestTestClient.post()
				.uri(UriComponentsBuilder.fromPath("/api/datasources/proxy/uid/tempo/api/search")
					.queryParam("q", "{resource.service.name=\"TestSpringBootDemo\"}")
					.build()
					.toUri())
				.header(HttpHeaders.AUTHORIZATION, authHeader)
				.exchange()
				.expectBody()
				.jsonPath("traces[0].spanSet.spans")
				.value(JSONArray.class, value -> assertThat(value).hasSize(1))
				.jsonPath("traces[0].rootTraceName")
				.isEqualTo("GET /greetings"));

		Awaitility.given()
			.pollInterval(Duration.ofSeconds(2))
			.atMost(Duration.ofSeconds(15))
			.ignoreExceptions()
			.untilAsserted(() -> lgtmRestTestClient.post()
				.uri(UriComponentsBuilder.fromPath("/api/datasources/proxy/uid/loki/loki/api/v1/query_range")
					.queryParam("query", "{service_name=\"TestSpringBootDemo\"} | foo=`bar` |= ``")
					.build()
					.toUri())
				.header(HttpHeaders.AUTHORIZATION, authHeader)
				.exchange()
				.expectBody()
				.jsonPath("data.result[0].values[0]")
				.value(JSONArray.class, value -> assertThat(value).contains("request...")));
	}

	private static Object getServiceAccountKey(RestTestClient restTestClient) {
		var encodeBasicAuth = HttpHeaders.encodeBasicAuth("admin", "admin", StandardCharsets.UTF_8);
		var authHeader = "Basic " + encodeBasicAuth;

		EntityExchangeResult<ServiceAccount> serviceAccountEntityExchangeResult = restTestClient.post()
			.uri("/api/serviceaccounts")
			.header(HttpHeaders.AUTHORIZATION, authHeader)
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "name": "grafana",
					  "role": "Viewer",
					  "isDisabled": false
					}
					""")
			.exchange()
			.returnResult(ServiceAccount.class);
		var serviceAccount = serviceAccountEntityExchangeResult.getResponseBody();

		var tokenEntityExchangeResult = restTestClient.post()
			.uri("/api/serviceaccounts/{id}/tokens", serviceAccount.id())
			.header(HttpHeaders.AUTHORIZATION, authHeader)
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON)
			.body("""
					{
						"name": "grafana"
					}
					""")
			.exchange()
			.returnResult(Token.class);
		var token = tokenEntityExchangeResult.getResponseBody();

		return token.key();
	}

	record ServiceAccount(String id) {

	}

	record Token(String key) {

	}

}
