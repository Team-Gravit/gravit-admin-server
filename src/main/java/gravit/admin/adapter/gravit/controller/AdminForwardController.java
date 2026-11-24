package gravit.admin.adapter.gravit.controller;

import gravit.admin.adapter.gravit.controller.docs.AdminForwardControllerDocs;
import gravit.admin.adapter.gravit.dto.internal.GravitRequestDto;
import gravit.admin.adapter.gravit.dto.internal.GravitResponseDto;
import gravit.admin.adapter.gravit.infrastructure.GravitClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.PATCH;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminForwardController implements AdminForwardControllerDocs {

    private final GravitClient gravitClient;

    @RequestMapping(value = "/**", method = {GET, POST, PUT, PATCH, DELETE})
    public ResponseEntity<byte[]> forward(
            HttpMethod method,
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @RequestBody(required = false) byte[] body
    ) {
        GravitResponseDto response = gravitClient.exchange(GravitRequestDto.of(
                method,
                request.getRequestURI(),
                request.getQueryString(),
                contentType,
                accept,
                body
        ));

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status());
        if (response.contentType() != null) {
            builder.header(HttpHeaders.CONTENT_TYPE, response.contentType());
        }
        return response.hasBody() ? builder.body(response.body()) : builder.build();
    }
}
