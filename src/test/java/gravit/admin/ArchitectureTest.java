package gravit.admin;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모듈 사이 허용 의존 표(설계 ADMIN_SERVER_DESIGN 4-5, .claude/rules/project-structure.md "모듈 사이 의존")를 강제한다.
 * 표가 바뀌면 TABLE과 조건(△)만 고친다. 아직 없는 모듈도 규칙은 미리 둔다.
 */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "gravit.admin";

    private static final List<String> MODULES = List.of(
            "global", "job", "auth", "adapter", "qa", "insight", "support", "member", "content", "audit");

    // 행이 열을 import할 수 있는가. O 허용, X 금지, △n 조건부, - 자기 자신
    private static final Map<String, List<String>> TABLE = table(
            //            global job   auth  adapter qa   insight support member content audit
            row("global",  "-",  "X",  "X",  "X",   "X",  "X",   "X",   "X",   "X",   "X"),
            row("job",     "O",  "-",  "X",  "X",   "X",  "X",   "X",   "X",   "X",   "X"),
            row("auth",    "O",  "X",  "-",  "X",   "X",  "X",   "X",   "X",   "X",   "X"),
            row("adapter", "O",  "△1", "X",  "-",   "X",  "X",   "X",   "X",   "X",   "X"),
            row("qa",      "O",  "O",  "△2", "O",   "-",  "X",   "X",   "X",   "X",   "X"),
            row("insight", "O",  "O",  "△2", "O",   "X",  "-",   "△3",  "△3",  "△3",  "X"),
            row("support", "O",  "O",  "△2", "O",   "X",  "X",   "-",   "X",   "X",   "△4"),
            row("member",  "O",  "O",  "△2", "O",   "X",  "X",   "X",   "-",   "X",   "△4"),
            row("content", "O",  "O",  "△2", "O",   "X",  "X",   "X",   "X",   "-",   "△4"),
            row("audit",   "O",  "O",  "△2", "X",   "X",  "X",   "X",   "X",   "X",   "-"));

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    @Nested
    @DisplayName("허용 의존 표를 검사할 때")
    class DependencyTable {

        @TestFactory
        @DisplayName("표의 X 칸과 △ 칸마다 규칙을 지킨다")
        List<DynamicTest> 표의_금지_칸과_조건부_칸을_지킨다() {
            List<DynamicTest> tests = new ArrayList<>();
            TABLE.forEach((from, cells) -> {
                for (int i = 0; i < MODULES.size(); i++) {
                    String to = MODULES.get(i);
                    String cell = cells.get(i);
                    if (cell.equals("-") || cell.equals("O")) {
                        continue;
                    }
                    tests.add(DynamicTest.dynamicTest(
                            from + " → " + to + " (" + cell + ")",
                            () -> ruleFor(from, to, cell).check(CLASSES)));
                }
            });
            return tests;
        }

        @Test
        void 표는_모든_모듈의_행과_열을_가진다() {
            // when & then
            assertThat(TABLE.keySet()).containsExactlyElementsOf(MODULES);
            TABLE.forEach((from, cells) -> {
                assertThat(cells).hasSize(MODULES.size());
                assertThat(cells.get(MODULES.indexOf(from))).isEqualTo("-");
            });
        }

        @Test
        void 기준_패키지의_클래스는_표의_모듈_중_하나에_속한다() {
            // given
            String[] packages = MODULES.stream().map(ArchitectureTest::modulePackage).toArray(String[]::new);

            // when & then
            classes().that().resideInAPackage(BASE_PACKAGE + ".*..")
                    .should().resideInAnyPackage(packages)
                    .because("새 최상위 패키지는 허용 의존 표에 모듈로 올린 뒤에 만든다")
                    .check(CLASSES);
        }
    }

    private static ArchRule ruleFor(String from, String to, String cell) {
        DescribedPredicate<JavaClass> target = resideInAPackage(modulePackage(to));
        if (!cell.equals("X")) {
            target = target.and(not(allowedBy(cell, to)));
        }
        return noClasses().that().resideInAPackage(modulePackage(from))
                .should().dependOnClassesThat(target)
                .because("설계 4-5 허용 의존 표 " + from + " → " + to + " = " + cell)
                .allowEmptyShould(true);
    }

    private static DescribedPredicate<JavaClass> allowedBy(String condition, String to) {
        return switch (condition) {
            // △1 adapter → job: NonRetryableJobException 하나만
            case "△1" -> DescribedPredicate.describe("job.support.NonRetryableJobException",
                    c -> c.getName().equals(BASE_PACKAGE + ".job.support.NonRetryableJobException"));
            // △2 → auth: auth.principal(LoginAdmin)과 요청 토큰 보관 타입만. 필터, 캐시는 금지
            case "△2" -> resideInAPackage(BASE_PACKAGE + ".auth.principal..");
            // △3 insight → support, member, content: 각 모듈 service/*QueryService만
            case "△3" -> DescribedPredicate.describe(to + ".service.*QueryService",
                    c -> c.getPackageName().equals(BASE_PACKAGE + "." + to + ".service")
                            && c.getSimpleName().endsWith("QueryService"));
            // △4 → audit: audit.service.AuditRecorder 하나만
            case "△4" -> DescribedPredicate.describe("audit.service.AuditRecorder",
                    c -> c.getName().equals(BASE_PACKAGE + ".audit.service.AuditRecorder"));
            default -> throw new IllegalArgumentException("알 수 없는 칸: " + condition);
        };
    }

    private static String modulePackage(String module) {
        return BASE_PACKAGE + "." + module + "..";
    }

    private static Map.Entry<String, List<String>> row(String from, String... cells) {
        return Map.entry(from, List.of(cells));
    }

    @SafeVarargs
    private static Map<String, List<String>> table(Map.Entry<String, List<String>>... rows) {
        Map<String, List<String>> table = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> row : rows) {
            table.put(row.getKey(), row.getValue());
        }
        return table;
    }
}
