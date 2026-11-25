package gravit.admin.support.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

/**
 * 메인과 관리자 서버의 응답을 비교해 다른 곳을 경로 단위로 돌려준다(설계 9-2).
 * 상태 코드와 JSON 트리를 본다. 키 순서만 무시하고 값, 타입(숫자 종류 포함), null, 문자열 형식, 배열 순서는 그대로 본다.
 * JSON이 아니면 본문 바이트를 그대로 비교한다. 차이 메시지의 값은 앞부분만 남긴다(prod 데이터가 출력에 길게 남지 않게).
 */
public final class ContractComparator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ROOT = "$";
    private static final int VALUE_PREVIEW_LENGTH = 60;

    private ContractComparator() {
    }

    public static List<String> compare(
            ContractResponse main,
            ContractResponse admin
    ) {
        List<String> differences = new ArrayList<>();
        if (main.status() != admin.status()) {
            differences.add("status: main=" + main.status() + ", admin=" + admin.status());
        }

        JsonNode mainJson = readJson(main.body());
        JsonNode adminJson = readJson(admin.body());
        if (mainJson == null || adminJson == null) {
            if (!Arrays.equals(main.body(), admin.body())) {
                differences.add("body: JSON이 아니고 바이트가 다릅니다 (main " + main.body().length + "바이트, admin " + admin.body().length + "바이트)");
            }
            return differences;
        }

        compareNode(ROOT, mainJson, adminJson, differences);
        return differences;
    }

    private static void compareNode(
            String path,
            JsonNode main,
            JsonNode admin,
            List<String> differences
    ) {
        if (main.getNodeType() != admin.getNodeType()) {
            differences.add(path + ": 타입이 다릅니다 main=" + describe(main) + ", admin=" + describe(admin));
            return;
        }

        if (main.isObject()) {
            compareObject(path, main, admin, differences);
            return;
        }
        if (main.isArray()) {
            compareArray(path, main, admin, differences);
            return;
        }
        if (main.isNumber() && main.numberType() != admin.numberType()) {
            differences.add(path + ": 숫자 종류가 다릅니다 main=" + describe(main) + ", admin=" + describe(admin));
            return;
        }
        if (!main.equals(admin)) {
            differences.add(path + ": 값이 다릅니다 main=" + preview(main) + ", admin=" + preview(admin));
        }
    }

    private static void compareObject(
            String path,
            JsonNode main,
            JsonNode admin,
            List<String> differences
    ) {
        TreeSet<String> keys = new TreeSet<>();
        main.fieldNames().forEachRemaining(keys::add);
        admin.fieldNames().forEachRemaining(keys::add);

        for (String key : keys) {
            String childPath = path + "." + key;
            if (!main.has(key)) {
                differences.add(childPath + ": admin에만 있습니다");
            } else if (!admin.has(key)) {
                differences.add(childPath + ": main에만 있습니다");
            } else {
                compareNode(childPath, main.get(key), admin.get(key), differences);
            }
        }
    }

    private static void compareArray(
            String path,
            JsonNode main,
            JsonNode admin,
            List<String> differences
    ) {
        if (main.size() != admin.size()) {
            differences.add(path + ": 배열 길이가 다릅니다 main=" + main.size() + ", admin=" + admin.size());
        }
        Iterator<JsonNode> mainElements = main.elements();
        Iterator<JsonNode> adminElements = admin.elements();
        for (int index = 0; mainElements.hasNext() && adminElements.hasNext(); index++) {
            compareNode(path + "[" + index + "]", mainElements.next(), adminElements.next(), differences);
        }
    }

    private static JsonNode readJson(byte[] body) {
        if (body.length == 0) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }

    private static String describe(JsonNode node) {
        String type = node.isNumber() ? node.getNodeType() + "(" + node.numberType() + ")" : node.getNodeType().toString();
        return type + " " + preview(node);
    }

    private static String preview(JsonNode node) {
        String text = node.toString();
        return text.length() <= VALUE_PREVIEW_LENGTH ? text : text.substring(0, VALUE_PREVIEW_LENGTH) + "...";
    }
}
