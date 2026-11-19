package com.chubb.claims.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
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

    static final String GROUP = "claims";

    private static final String BASIC_AUTH = "basicAuth";
    private static final String PROBLEM = "Problem";
    private static final String PROBLEM_JSON = "application/problem+json";

    private static final String DESCRIPTION = """
            System of record for motor and property claims across six APAC markets (SG, MY, HK, TH, ID, PH).
            Every write goes through this API; claim events are published to Kafka for the insights service.

            **Authentication.** HTTP Basic on every request (demo users: `claimant1`, `officer1`, `lead1`, \
            `manager1`, password `password`). Click **Authorize** to try requests from this page.

            **Roles.** `CLAIMANT` sees only their own claims. `OFFICER` and `MANAGER` see unassigned claims plus \
            claims of people below them in the reporting line. Anything outside your scope returns **404**, not \
            403, so its existence is not revealed.

            **Rules live on the server.** Each claim response lists `allowedActions` for the caller, who it is \
            `waitingOn` and the `nextStep`, so clients never re-implement the workflow.

            **Errors** are RFC 7807 Problem Details (`application/problem+json`): 400 validation (with an \
            `errors` map per field), 401, 403, 404, 409 invalid state or concurrent update, 413 file too large, \
            415 unsupported file, 422 business rule.

            **Idempotency.** Send an `Idempotency-Key` header when submitting a claim; a retry returns the \
            original claim.

            **Paging.** List endpoints take `page` (from 0) and `size` (1-100) and return `content`, `page`, \
            `size`, `totalElements`, `totalPages`.
            """;

    @Bean
    OpenAPI claimsServiceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Claims Service API").version("v1").description(DESCRIPTION))
                .servers(List.of(new Server().url("/").description("Same origin as the web app")))
                .tags(List.of(
                        new Tag().name("Claims").description("Submit, track and progress claims through review, "
                                + "assessment, decision and settlement."),
                        new Tag().name("Approvals").description("Approvals above an officer's limit, reviewed by "
                                + "someone above them in the reporting line (four-eyes)."),
                        new Tag().name("Documents"),
                        new Tag().name("Employees").description("Directory and profiles, scoped to yourself and "
                                + "the people below you."),
                        new Tag().name("Current user").description("Who is signed in.")))
                .components(new Components()
                        .addSecuritySchemes(BASIC_AUTH, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic"))
                        .addSchemas(PROBLEM, problemSchema())
                        .addResponses("BadRequest", problem("Validation failed; see `errors` for each field"))
                        .addResponses("Unauthorized", problem("Missing or invalid credentials"))
                        .addResponses("Forbidden", problem("Your role cannot call this endpoint"))
                        .addResponses("NotFound", problem("Not found, or outside what you are allowed to see")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }

    @Bean
    GroupedOpenApi claimsApiGroup() {
        return GroupedOpenApi.builder()
                .group(GROUP)
                .displayName("Claims service")
                .pathsToMatch("/api/v1/**")
                .addOpenApiCustomizer(standardErrorResponses())
                .build();
    }

    static OpenApiCustomizer standardErrorResponses() {
        return openApi -> openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach(
                (method, operation) -> addStandardResponses(path, method, operation)));
    }

    private static void addStandardResponses(String path, PathItem.HttpMethod method, Operation operation) {
        ApiResponses responses = operation.getResponses();
        boolean writes = method == PathItem.HttpMethod.POST || method == PathItem.HttpMethod.PATCH
                || method == PathItem.HttpMethod.PUT;
        boolean takesInput = writes || path.contains("{")
                || (operation.getParameters() != null && !operation.getParameters().isEmpty());
        addIfAbsent(responses, "400", "BadRequest", takesInput);
        addIfAbsent(responses, "401", "Unauthorized", true);
        addIfAbsent(responses, "403", "Forbidden", true);
        addIfAbsent(responses, "404", "NotFound", path.contains("{"));
    }

    private static void addIfAbsent(ApiResponses responses, String code, String component, boolean applies) {
        if (applies && !responses.containsKey(code)) {
            responses.addApiResponse(code, new ApiResponse().$ref("#/components/responses/" + component));
        }
    }

    private static ApiResponse problem(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType(PROBLEM_JSON,
                new MediaType().schema(new ObjectSchema().$ref("#/components/schemas/" + PROBLEM))));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ObjectSchema problemSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.description("RFC 7807 Problem Details");
        schema.properties(Map.of(
                "type", new StringSchema().example("about:blank"),
                "title", new StringSchema().example("Validation failed"),
                "status", new IntegerSchema().example(400),
                "detail", new StringSchema().example("One or more fields are invalid."),
                "instance", new StringSchema().example("/api/v1/claims"),
                "errors", new MapSchema().additionalProperties(new StringSchema())
                        .description("Field name to message, for validation errors only")));
        return schema;
    }
}
