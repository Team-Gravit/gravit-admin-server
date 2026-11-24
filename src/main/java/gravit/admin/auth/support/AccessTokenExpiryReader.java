package gravit.admin.auth.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AccessTokenExpiryReader {

    private static final Pattern PART_DELIMITER = Pattern.compile("\\.");
    private static final int JWS_PART_COUNT = 3;
    private static final int PAYLOAD_INDEX = 1;
    private static final String EXPIRY_CLAIM = "exp";

    private final ObjectMapper objectMapper;

    public Optional<Instant> read(String accessToken) {
        String[] parts = PART_DELIMITER.split(accessToken, -1);
        if (parts.length != JWS_PART_COUNT) {
            return Optional.empty();
        }

        try {
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[PAYLOAD_INDEX]));
            JsonNode expiry = payload.path(EXPIRY_CLAIM);
            if (!expiry.isNumber()) {
                return Optional.empty();
            }
            return Optional.of(Instant.ofEpochSecond(expiry.asLong()));
        } catch (IOException | IllegalArgumentException | DateTimeException e) {
            return Optional.empty();
        }
    }
}
