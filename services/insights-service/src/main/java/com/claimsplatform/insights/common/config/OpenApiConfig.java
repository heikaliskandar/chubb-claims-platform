package com.claimsplatform.insights.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    static final String GROUP = "insights";

    private static final String BASIC_AUTH = "basicAuth";
    private static final String PROBLEM = "Problem";

    private static final String DESCRIPTION = """
            Read-only dashboards built from claim events on Kafka (`claims.claim-events.v1`).

            **Eventually consistent.** Every report carries `asOf`, the time of the latest event applied. A change \
            made through the claims service usually appears within a second or two. Read a single claim from the \
            claims service when you need its exact current state.

            **No personal data.** The read model holds identifiers, codes, amounts and timestamps only.

            **Money is never mixed.** Amounts are always reported per currency; there is no FX conversion.

            **Authentication.** HTTP Basic, same demo users as the claims service. Workload and performance are \
            for `OFFICER` and `MANAGER`; exposure is `MANAGER` only. Errors are RFC 7807 Problem Details.
            """;

    @Bean
    OpenAPI insightsServiceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Insights Service API").version("v1").description(DESCRIPTION))
                .servers(List.of(new Server().url("/").description("Same origin as the web app")))
                .tags(List.of(new Tag().name("Insights")
                        .description("Workload, performance and liability exposure, aggregated per currency.")))
                .components(new Components()
                        .addSecuritySchemes(BASIC_AUTH, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic"))
                        .addSchemas(PROBLEM, problemSchema())
                        .addResponses("Unauthorized", problem("Missing or invalid credentials"))
                        .addResponses("Forbidden", problem("Your role cannot read this report")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }

    @Bean
    GroupedOpenApi insightsApiGroup() {
        return GroupedOpenApi.builder()
                .group(GROUP)
                .displayName("Insights service")
                .pathsToMatch("/api/v1/**")
                .addOpenApiCustomizer(standardErrorResponses())
                .build();
    }

    static OpenApiCustomizer standardErrorResponses() {
        return openApi -> openApi.getPaths().values().forEach(item -> item.readOperations().forEach(operation -> {
            ApiResponses responses = operation.getResponses();
            responses.putIfAbsent("401", new ApiResponse().$ref("#/components/responses/Unauthorized"));
            responses.putIfAbsent("403", new ApiResponse().$ref("#/components/responses/Forbidden"));
        }));
    }

    private static ApiResponse problem(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new MediaType().schema(new ObjectSchema().$ref("#/components/schemas/" + PROBLEM))));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ObjectSchema problemSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.description("RFC 7807 Problem Details");
        schema.properties(Map.of(
                "type", new StringSchema().example("about:blank"),
                "title", new StringSchema().example("Forbidden"),
                "status", new IntegerSchema().example(403),
                "detail", new StringSchema().example("Access denied"),
                "instance", new StringSchema().example("/api/v1/insights/exposure")));
        return schema;
    }
}
