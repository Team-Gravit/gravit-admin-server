package gravit.admin.adapter.gravit.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Forward API", description = "관리자 서버가 직접 처리하지 않는 관리자 API를 메인 서버로 전달")
public interface AdminForwardControllerDocs {

    @Operation(
            summary = "관리자 API 메인 전달",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>메서드, 경로, 쿼리, 본문과 Authorization, Content-Type, Accept만 메인에 보내고 "
                    + "메인 응답의 상태, Content-Type, 본문을 그대로 돌려준다. 연결 실패는 502 GRAVIT_5021, 응답 확인 실패는 504 GRAVIT_5041"
    )
    ResponseEntity<byte[]> forward(
            @Parameter(hidden = true) HttpMethod method,
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) String contentType,
            @Parameter(hidden = true) String accept,
            @Parameter(hidden = true) byte[] body
    );
}
