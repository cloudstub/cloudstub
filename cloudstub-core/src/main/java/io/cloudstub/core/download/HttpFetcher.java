package io.cloudstub.core.download;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Fetches bytes over HTTP, distinguishing a 404 from other transport failures. */
final class HttpFetcher {

    private final HttpClient http;

    HttpFetcher() {
        this.http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    byte[] get(String url) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .GET()
                        .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status == 404) {
            throw new NotFoundException();
        }
        if (status / 100 != 2) {
            throw new IOException("HTTP " + status + " from " + url);
        }
        return response.body();
    }

    /** Signals an HTTP 404, distinguishing "not published" from other transport failures. */
    static final class NotFoundException extends IOException {}
}
