package org.example;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;

/**
 * Утилита для создания SSLContext, который доверяет всем сертификатам (НЕ использовать в продакшене!)
 */
public class UnsafeSslUtil {
    /**
     * Возвращает SSLContext, который доверяет любым сертификатам (для HttpClient и других клиентов).
     */
    public static SSLContext getUnsafeSslContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
            }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        return sslContext;
    }

    /**
     * Возвращает HttpClient, который доверяет любым сертификатам и отключает проверку имени хоста (hostname verification).
     * Работает для Java 11+ HttpClient. НЕ использовать в продакшене!
     */
    public static HttpClient getUnsafeHttpClient() throws Exception {
        SSLContext sslContext = getUnsafeSslContext();
        HttpClient.Builder builder = HttpClient.newBuilder().sslContext(sslContext);
        // Отключаем hostname verification через reflection
        SSLParameters sslParams = sslContext.getDefaultSSLParameters();
        try {
            java.lang.reflect.Field field = sslParams.getClass().getDeclaredField("endpointIdentificationAlgorithm");
            field.setAccessible(true);
            field.set(sslParams, null);
            builder.sslParameters(sslParams);
        } catch (Exception ignore) {
            // Если вдруг не удалось — просто продолжаем с доверяющим SSLContext
        }
        return builder.build();
    }
}
