package com.everythingcanbe.linebotclinicnotifysystem.provider.wuobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.everythingcanbe.linebotclinicnotifysystem.config.ClinicProperties;
import com.everythingcanbe.linebotclinicnotifysystem.parser.XmlRoomStatusParser;
import com.everythingcanbe.linebotclinicnotifysystem.provider.ClinicProvider;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

/**
 * 慈心吳婦產科：讀取 AWS S3 上的 MedicineNumberListNN.xml。
 * 每個診間保存 ETag，資料未變動時 S3 回應 304，沿用上次內容。
 */
@Component
@ConditionalOnProperty(name = "clinic.provider", havingValue = WuObsClinicProvider.CODE, matchIfMissing = true)
public class WuObsClinicProvider implements ClinicProvider {

    public static final String CODE = "wuobs";

    private static final Logger log = LoggerFactory.getLogger(WuObsClinicProvider.class);

    private final RestClient restClient;
    private final XmlRoomStatusParser parser;
    private final ClinicProperties properties;
    private final Map<Integer, CachedContent> contentCache = new ConcurrentHashMap<>();

    public WuObsClinicProvider(RestClient clinicRestClient, XmlRoomStatusParser parser, ClinicProperties properties) {
        this.restClient = clinicRestClient;
        this.parser = parser;
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RoomStatus> fetchAllRooms() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<RoomStatus>> futures = properties.rooms().stream()
                    .map(roomId -> executor.submit(() -> fetchRoom(roomId)))
                    .toList();
            List<RoomStatus> result = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    result.add(futures.get(i).get());
                } catch (ExecutionException e) {
                    log.warn("Failed to fetch room {}: {}", properties.rooms().get(i), e.getCause().toString());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return result;
        }
    }

    @Override
    public RoomStatus fetchRoom(Integer roomId) {
        String url = String.format(properties.wuobs().urlTemplate(), roomId)
                + "?rand=" + ThreadLocalRandom.current().nextDouble();
        CachedContent cached = contentCache.get(roomId);

        ResponseEntity<byte[]> response = restClient.get()
                .uri(url)
                .headers(headers -> {
                    if (cached != null && cached.etag() != null) {
                        headers.setIfNoneMatch(cached.etag());
                    }
                })
                .retrieve()
                .toEntity(byte[].class);

        byte[] body;
        if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_MODIFIED) && cached != null) {
            body = cached.body();
        } else {
            body = Objects.requireNonNull(response.getBody(), "Empty response body");
            contentCache.put(roomId, new CachedContent(response.getHeaders().getFirst(HttpHeaders.ETAG), body));
        }
        // 每次都重新解析：「是否為當日資料」會隨時間改變
        return parser.parse(body, CODE);
    }

    private record CachedContent(String etag, byte[] body) {
    }

}
