package gravit.admin.adapter.openapi.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.admin.adapter.openapi.config.OpenApiProps;
import gravit.admin.adapter.openapi.dto.internal.OpenApiDocumentDto;
import gravit.admin.adapter.openapi.dto.internal.OpenApiExcerptDto;
import gravit.admin.adapter.openapi.support.OpenApiExtractor;
import gravit.admin.adapter.support.ExternalCallExecutor;
import gravit.admin.adapter.support.ExternalResponses;
import gravit.admin.adapter.support.HttpRequestFactories;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.util.Collection;

import static gravit.admin.adapter.domain.ExternalApi.OPENAPI;

@Component
public class OpenApiClient {

    private static final String OPERATION = "GET /v3/api-docs";

    private final RestClient restClient;

    private final ExternalCallExecutor externalCallExecutor;

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final OpenApiProps openApiProps;

    public OpenApiClient(
            RestClient.Builder restClientBuilder,
            ExternalCallExecutor externalCallExecutor,
            Clock clock,
            ObjectMapper objectMapper,
            OpenApiProps openApiProps
    ) {
        this.restClient = restClientBuilder
                .requestFactory(HttpRequestFactories.jdk(openApiProps.timeout()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.externalCallExecutor = externalCallExecutor;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.openApiProps = openApiProps;
    }

    public OpenApiDocumentDto fetchApiDocs() {
        URI uri = URI.create(openApiProps.url());
        String rawJson = externalCallExecutor.execute(OPENAPI, OPERATION, () -> restClient.get()
                .uri(uri)
                .exchange((request, response) -> ExternalResponses.readText(OPENAPI, OPERATION, response, clock)));

        JsonNode document = ExternalResponses.parseJson(objectMapper, OPENAPI, OPERATION, rawJson);
        return new OpenApiDocumentDto(openApiProps.url(), document, rawJson);
    }

    public OpenApiExcerptDto extract(
            OpenApiDocumentDto apiDocs,
            Collection<String> apis
    ) {
        return OpenApiExtractor.extract(apiDocs.document(), apis);
    }
}
