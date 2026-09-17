package com.example.uniswap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.uniswap.config.UniswapV3Config;
import com.example.uniswap.model.SwapData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SupabaseEventPersistence {
    private static final Logger log = LoggerFactory.getLogger(SupabaseEventPersistence.class);

    private final String endpoint;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public SupabaseEventPersistence(
            @Value("${supabase.url}") String url,
            @Value("${supabase.api-key}") String apiKey,
            ObjectMapper objectMapper) {
        this.endpoint = url + "/rest/v1/uniswap_events";
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    public void saveSwap(SwapData data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_type", "SWAP");
        payload.put("pool_address", UniswapV3Config.POOL_ADDRESS);
        payload.put("transaction_hash", data.transactionHash);
        payload.put("block_number", Long.parseLong(data.blockNumber));
        payload.put("payload", data);
        post(payload);
    }

    public void saveRawEvent(String eventType, String transactionHash, String blockNumber, Map<String, Object> payload) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("event_type", eventType);
        row.put("pool_address", UniswapV3Config.POOL_ADDRESS);
        row.put("transaction_hash", transactionHash);
        row.put("block_number", Long.parseLong(blockNumber));
        row.put("payload", payload);
        post(row);
    }

    private void post(Map<String, Object> row) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("未配置 SUPABASE_API_KEY，跳过事件落库");
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(row)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.error("Supabase 事件落库失败: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception exception) {
            log.error("Supabase 事件落库异常", exception);
        }
    }
}
