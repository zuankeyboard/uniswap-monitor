package com.example.uniswap.controller;

import com.example.uniswap.model.PoolState;
import com.example.uniswap.model.SwapData;
import com.example.uniswap.service.PoolQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PoolController {
    private final PoolQueryService queryService;

    public PoolController(PoolQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/pool/state")
    public PoolState getPoolState() throws Exception {
        return queryService.getPoolState();
    }

    @GetMapping("/swaps/history")
    public ResponseEntity<Map<String, Object>> getHistoricalSwaps(
            @RequestParam(required = false) String fromBlock,
            @RequestParam(required = false) String toBlock,
            @RequestParam(defaultValue = "20") int limit) throws Exception {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit 必须在 1 到 1000 之间");
        }

        List<SwapData> swaps;
        if (fromBlock == null && toBlock == null) {
            swaps = queryService.getLatestSwaps(limit);
        } else if (fromBlock != null && toBlock != null) {
            BigInteger from = parseBlockNumber(fromBlock);
            BigInteger to = parseBlockNumber(toBlock);
            if (from.compareTo(to) > 0) {
                throw new IllegalArgumentException("fromBlock 不能大于 toBlock");
            }
            swaps = queryService.getHistoricalSwaps(from, to, limit);
        } else {
            throw new IllegalArgumentException("fromBlock 和 toBlock 必须同时提供");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("message", "success");
        response.put("data", swaps);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pool/state/readable")
    public Map<String, String> getPoolStateReadable() throws Exception {
        PoolState raw = queryService.getPoolState();
        Map<String, String> readable = new LinkedHashMap<>();
        BigDecimal price = new BigDecimal(raw.sqrtPriceX96);
        readable.put("price", price.setScale(2, RoundingMode.HALF_UP).toPlainString());
        readable.put("tick", raw.tick);
        readable.put("usdtBalance", raw.usdtBalance);
        readable.put("wethBalance", raw.wethBalance);
        readable.put("liquidity", String.format("%,d", new BigInteger(raw.liquidity)));
        readable.put("blockNumber", raw.blockNumber);
        readable.put("updatedAt", raw.updatedAt);
        return readable;
    }

    private BigInteger parseBlockNumber(String block) {
        return block.startsWith("0x")
                ? new BigInteger(block.substring(2), 16)
                : new BigInteger(block);
    }
}
