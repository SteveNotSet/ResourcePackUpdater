package cn.zbx1425.resourcepackupdater.io.network;

import cn.zbx1425.resourcepackupdater.ResourcePackUpdater;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public class DownloadTask {

    public long totalBytes;
    public long downloadedBytes;
    public final long expectedSize;

    protected final DownloadDispatcher dispatcher;
    private final URI requestUri;

    public String fileName;

    public int failedAttempts = 0;

    public DownloadTask(DownloadDispatcher dispatcher, String url, String fileName, long expectedSize) {
        this.dispatcher = dispatcher;
        this.requestUri = URI.create(url);
        this.fileName = fileName;
        this.expectedSize = expectedSize;
    }

    public void runBlocking(OutputStream target) throws IOException {
        // ResourcePackUpdater.LOGGER.info("Starting download: " + fileName);
        HttpResponse<InputStream> httpResponse = sendHttpRequest(requestUri);

        if (httpResponse.statusCode() >= 400) {
            throw new IOException("Server returned HTTP " + httpResponse.statusCode() + " "
                    + new String(httpResponse.body().readAllBytes(), StandardCharsets.UTF_8));
        }

        totalBytes = Long.parseLong(httpResponse.headers().firstValue("Content-Length").orElse(Long.toString(expectedSize)));
        final long[] accountedAmount = {0};

        try {
            try (BufferedOutputStream bos = new BufferedOutputStream(target); InputStream inputStream = unwrapHttpResponse(httpResponse)) {
                final ProgressOutputStream pOfs = new ProgressOutputStream(bos, new ProgressOutputStream.WriteListener() {
                    final long noticeDivisor = 8192;

                    @Override
                    public void registerWrite(long amountOfBytesWritten) throws IOException {
                        if (accountedAmount[0] / noticeDivisor != amountOfBytesWritten / noticeDivisor) {
                            downloadedBytes += (amountOfBytesWritten - accountedAmount[0]);
                            dispatcher.onDownloadProgress((amountOfBytesWritten - accountedAmount[0]));
                            accountedAmount[0] = amountOfBytesWritten;
                        }
                    }
                });
                IOUtils.copy(new BufferedInputStream(inputStream), pOfs);
            }
            target.close();
        } catch (Exception ex) {
            dispatcher.onDownloadProgress(-accountedAmount[0]);
            downloadedBytes = 0;
            throw ex;
        }
        dispatcher.onDownloadProgress(totalBytes - accountedAmount[0]);
        downloadedBytes = totalBytes;
    }

    public static HttpResponse<InputStream> sendHttpRequest(URI requestUri) throws IOException {
        /*
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest((url.getPath() + "?REALLY-BAD-VALIDATION-IDEA")
                    .getBytes(StandardCharsets.UTF_8));
            String digestStr = StringUtils.stripEnd(Base64.getEncoder().encodeToString(digest).replace('+', '-').replace('/', '_'), "=");
            requestUri = new URIBuilder(url.toURI()).addParameter("md5", digestStr).build();
        } catch (Exception ex) {
            throw new IOException(ex);
        }
         */

        HttpRequest httpRequest = HttpRequest.newBuilder(requestUri)
                .timeout(Duration.ofSeconds(20))
                .setHeader("User-Agent", "ResourcePackUpdater/" + ResourcePackUpdater.MOD_VERSION + " +https://www.zbx1425.cn")
                .setHeader("Accept-Encoding", "gzip")
                .GET()
                .build();
        HttpResponse<InputStream> httpResponse;
        try {
            httpResponse = ResourcePackUpdater.HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            throw new IOException(ex);
        }
        return httpResponse;
    }

    public static InputStream unwrapHttpResponse(HttpResponse<InputStream> response) throws IOException {
        String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("").toLowerCase(Locale.ROOT);
        return switch (contentEncoding) {
            case "" -> response.body();
            case "gzip" -> new GZIPInputStream(response.body());
            default -> throw new IOException("Unsupported Content-Encoding: " + contentEncoding);
        };
    }
}
