package com.claimsplatform.claims.common.config;

import com.claimsplatform.claims.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
        "app.security.demo-users.password=test-password",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractIT {

    private static final Path CONTRACT = Path.of("..", "..", "docs", "api", "claims-service.openapi.json");
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Autowired
    private MockMvc mvc;

    private JsonNode liveSpec() throws Exception {
        String body = mvc.perform(get("/v3/api-docs/" + OpenApiConfig.GROUP))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JSON.readTree(body);
    }

    @Test
    void everyOperationIsDocumentedWithASummaryATagAndItsErrors() throws Exception {
        List<String> problems = new ArrayList<>();
        liveSpec().get("paths").properties().forEach(path -> path.getValue().properties().forEach(operation -> {
            String name = operation.getKey().toUpperCase() + " " + path.getKey();
            JsonNode node = operation.getValue();
            if (!node.hasNonNull("summary") || node.get("summary").asString().isBlank()) {
                problems.add(name + " has no summary");
            }
            if (!node.has("tags") || node.get("tags").isEmpty()) {
                problems.add(name + " has no tag");
            }
            if (!node.get("responses").has("401")) {
                problems.add(name + " does not document 401");
            }
            if (path.getKey().contains("{") && !node.get("responses").has("404")) {
                problems.add(name + " does not document 404");
            }
        }));
        assertThat(problems).isEmpty();
    }

    @Test
    void theSwaggerUiOffersBothServices() throws Exception {
        JsonNode config = JSON.readTree(mvc.perform(get("/v3/api-docs/swagger-config"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(config.get("urls").findValuesAsString("url"))
                .contains("/v3/api-docs/claims", "/v3/api-docs/insights");
    }

    @Test
    void theCommittedContractMatchesTheCode() throws Exception {
        JsonNode live = liveSpec();
        if (Boolean.getBoolean("openapi.update") || !Files.exists(CONTRACT)) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, JSON.writeValueAsString(live) + System.lineSeparator());
        }
        JsonNode committed = JSON.readTree(Files.readString(CONTRACT));

        assertThat(live)
                .withFailMessage("The API changed. Review the change, then run "
                        + "`./mvnw verify -Dopenapi.update=true` and commit docs/api/claims-service.openapi.json")
                .isEqualTo(committed);
    }
}
