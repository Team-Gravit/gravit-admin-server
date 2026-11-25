package gravit.admin.support.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContractComparatorTest {

    private static final String JSON = "application/json";

    @Nested
    @DisplayName("같다고 볼 때")
    class Same {

        @Test
        void 키_순서만_다르면_차이가_없다() {
            // when
            List<String> differences = compare(200, "{\"a\":1,\"b\":{\"x\":null,\"y\":\"2026-11-18T10:15:30\"}}",
                    200, "{\"b\":{\"y\":\"2026-11-18T10:15:30\",\"x\":null},\"a\":1}");

            // then
            assertThat(differences).isEmpty();
        }

        @Test
        void 본문이_없는_같은_상태는_차이가_없다() {
            // when
            List<String> differences = compare(204, "", 204, "");

            // then
            assertThat(differences).isEmpty();
        }
    }

    @Nested
    @DisplayName("다르다고 볼 때")
    class Different {

        @Test
        void 상태_코드가_다르면_알린다() {
            // when
            List<String> differences = compare(500, "{\"error\":\"GLOBAL_5001\"}", 404, "{\"error\":\"GLOBAL_5001\"}");

            // then
            assertThat(differences).containsExactly("status: main=500, admin=404");
        }

        @Test
        void 값이_다르면_경로와_함께_알린다() {
            // when
            List<String> differences = compare(200, "{\"page\":{\"totalPages\":3}}", 200, "{\"page\":{\"totalPages\":4}}");

            // then
            assertThat(differences).containsExactly("$.page.totalPages: 값이 다릅니다 main=3, admin=4");
        }

        @Test
        void 문자열과_숫자처럼_타입이_다르면_알린다() {
            // when
            List<String> differences = compare(200, "{\"id\":1}", 200, "{\"id\":\"1\"}");

            // then
            assertThat(differences).singleElement().asString().startsWith("$.id: 타입이 다릅니다");
        }

        @Test
        void 정수와_소수처럼_숫자_종류가_다르면_알린다() {
            // when
            List<String> differences = compare(200, "{\"averageProgress\":50}", 200, "{\"averageProgress\":50.0}");

            // then
            assertThat(differences).singleElement().asString().startsWith("$.averageProgress: 숫자 종류가 다릅니다");
        }

        @Test
        void null과_키_없음을_구분한다() {
            // when
            List<String> differences = compare(200, "{\"answer\":null,\"options\":[]}", 200, "{\"options\":[]}");

            // then
            assertThat(differences).containsExactly("$.answer: main에만 있습니다");
        }

        @Test
        void 시각_문자열_형식이_다르면_알린다() {
            // when
            List<String> differences = compare(200, "{\"createdAt\":\"2026-11-18T10:15:30\"}", 200, "{\"createdAt\":\"2026-11-18T10:15:30+09:00\"}");

            // then
            assertThat(differences).singleElement().asString().startsWith("$.createdAt: 값이 다릅니다");
        }

        @Test
        void 배열은_순서와_길이를_본다() {
            // when
            List<String> differences = compare(200, "{\"contents\":[1,2,3]}", 200, "{\"contents\":[2,1]}");

            // then
            assertThat(differences).containsExactly(
                    "$.contents: 배열 길이가 다릅니다 main=3, admin=2",
                    "$.contents[0]: 값이 다릅니다 main=1, admin=2",
                    "$.contents[1]: 값이 다릅니다 main=2, admin=1"
            );
        }

        @Test
        void JSON이_아니면_바이트를_비교한다() {
            // when
            List<String> differences = compare(200, "<html>a</html>", 200, "<html>b</html>");

            // then
            assertThat(differences).singleElement().asString().startsWith("body: JSON이 아니고 바이트가 다릅니다");
        }

        @Test
        void 긴_값은_앞부분만_남긴다() {
            // given
            String longValue = "x".repeat(200);

            // when
            List<String> differences = compare(200, "{\"content\":\"" + longValue + "\"}", 200, "{\"content\":\"y\"}");

            // then
            assertThat(differences).singleElement().asString().doesNotContain(longValue).contains("...");
        }
    }

    private static List<String> compare(
            int mainStatus,
            String mainBody,
            int adminStatus,
            String adminBody
    ) {
        return ContractComparator.compare(
                ContractResponse.of(mainStatus, JSON, mainBody.getBytes(StandardCharsets.UTF_8)),
                ContractResponse.of(adminStatus, JSON, adminBody.getBytes(StandardCharsets.UTF_8))
        );
    }
}
