package gravit.admin.adapter.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HttpRequestFactories {

    public static JdkClientHttpRequestFactory jdk(Duration timeout) {
        return jdk(timeout, timeout);
    }

    public static JdkClientHttpRequestFactory jdk(
            Duration connectTimeout,
            Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
