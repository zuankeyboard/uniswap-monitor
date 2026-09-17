package com.example.uniswap.service;

import com.example.uniswap.config.UniswapV3Config;
import com.example.uniswap.model.PoolState;
import com.example.uniswap.model.SwapData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Service
public class PoolQueryService {
    private final String endpoint;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public PoolQueryService(
            @Value("${supabase.url}") String url,
            @Value("${supabase.api-key}") String apiKey,
            ObjectMapper objectMapper) {
        this.endpoint = url + "/rest/v1/uniswap_events";
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    public PoolState getPoolState() throws Exception {
        String query = "?select=payload,block_number,created_at&event_type=eq.SWAP&pool_address=eq."
                + UniswapV3Config.POOL_ADDRESS + "&order=block_number.desc,id.desc&limit=1";
        List<JsonNode> rows = get(query);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Supabase 中暂无资金池状态数据");
        }
        JsonNode row = rows.get(0);
        JsonNode payload = row.path("payload");
        PoolState state = new PoolState();
        state.sqrtPriceX96 = payload.path("sqrtPriceX96").asText();
        state.tick = payload.path("tick").asText();
        state.liquidity = payload.path("liquidity").asText();
        state.usdtBalance = payload.path("usdtBalance").asText(null);
        state.wethBalance = payload.path("wethBalance").asText(null);
        state.blockNumber = row.path("block_number").asText();
        state.updatedAt = row.path("created_at").asText();
        return state;
    }

    public List<SwapData> getHistoricalSwaps(BigInteger fromBlock, BigInteger toBlock, int limit) throws Exception {
        String query = "?select=payload&event_type=eq.SWAP&pool_address=eq." + UniswapV3Config.POOL_ADDRESS
                + "&block_number=gte." + fromBlock + "&block_number=lte." + toBlock
                + "&order=block_number.asc,id.asc&limit=" + limit;
        List<SwapData> swaps = new ArrayList<>();
        for (JsonNode row : get(query)) {
            swaps.add(objectMapper.treeToValue(row.path("payload"), SwapData.class));
        }
        return swaps;
    }

    public List<SwapData> getLatestSwaps(int limit) throws Exception {
        String query = "?select=payload&event_type=eq.SWAP&pool_address=eq." + UniswapV3Config.POOL_ADDRESS
                + "&order=block_number.desc,id.desc&limit=" + limit;
        List<SwapData> swaps = new ArrayList<>();
        for (JsonNode row : get(query)) {
            swaps.add(objectMapper.treeToValue(row.path("payload"), SwapData.class));
        }
        return swaps;
    }

    private List<JsonNode> get(String query) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 supabase.api-key");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + query))
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Supabase 查询失败: " + response.statusCode() + " " + response.body());
        }
        return objectMapper.readValue(response.body(), objectMapper.getTypeFactory()
                .constructCollectionType(List.class, JsonNode.class));
    }
}
