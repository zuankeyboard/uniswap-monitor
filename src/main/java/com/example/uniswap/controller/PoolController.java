package com.example.uniswap.controller;

import com.example.uniswap.model.PoolState;
import com.example.uniswap.model.SwapData;
import com.example.uniswap.service.PoolQueryService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@RestController
public class PoolController {

    @Autowired
    private Web3j web3j; // 声明一个 Web3j 字段

    @Autowired
    private PoolQueryService queryService;

    // ... 你的接口方法
    // private final PoolQueryService queryService;

    public PoolController() {
        // 实际项目中建议使用单例 Web3j 实例
        Web3j web3j = Web3j.build(new HttpService("https://eth-mainnet.g.alchemy.com/v2/Pf_TDVX0AZrk_GqRLuwqL"));
        this.queryService = new PoolQueryService(web3j);
    }

    @GetMapping("/pool/state")
    public PoolState getPoolState() throws Exception {
        return queryService.getPoolState();
    }

    // 可增加更多端点，例如查询余额、查询历史等
    @GetMapping("/swaps/history")
    public ResponseEntity<Map<String, Object>> getHistoricalSwaps(
            @RequestParam(required = false) String fromBlock,
            @RequestParam(required = false) String toBlock,
            @RequestParam(defaultValue = "20") int limit) throws Exception {

        // 1. 处理区块范围
        BigInteger from, to;
        if (fromBlock != null && toBlock != null) {
            // 如果用户提供了范围，直接使用
            from = parseBlockNumber(fromBlock);
            to = parseBlockNumber(toBlock);
        } else {
            // 否则根据 limit 自动计算
            BigInteger latest = web3j.ethBlockNumber().send().getBlockNumber();
            to = latest;
            // 估算：假设每秒约 1 个区块，但为了简单，我们用 10 个区块范围（满足 Infura 免费限制）
            // 更准确的方法是先获取最近 limit 笔交易的实际区块号（但较复杂），这里用固定 10 块
            from = latest.subtract(BigInteger.valueOf(9));
            // 如果 limit 小于 10，可以缩小范围，但为了代码简洁，固定 10 块
        }

        // 2. 调用服务
        List<SwapData> swaps = queryService.getHistoricalSwaps(from, to);

        // 3. 如果提供了 limit，截取后 limit 条（因为 getHistoricalSwaps 返回的是 from->to 全部）
        if (swaps.size() > limit) {
            swaps = swaps.subList(swaps.size() - limit, swaps.size());
        }

        // 4. 包装响应
        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("message", "success");
        response.put("data", swaps);
        return ResponseEntity.ok(response);
    }

    // 辅助方法：解析区块号（支持十进制和十六进制）
    private BigInteger parseBlockNumber(String block) {
        if (block.startsWith("0x")) {
            return new BigInteger(block.substring(2), 16);
        } else {
            return new BigInteger(block);
        }
    }

    @GetMapping("/pool/state/readable")
    public Map<String, String> getPoolStateReadable() throws Exception {
        PoolState raw = queryService.getPoolState();
        Map<String, String> readable = new LinkedHashMap<>();
        // 价格计算
        BigDecimal sqrtPrice = new BigDecimal(new BigInteger(raw.sqrtPriceX96))
                .divide(new BigDecimal(2).pow(96), 18, RoundingMode.HALF_UP);
        BigDecimal price = sqrtPrice.multiply(sqrtPrice).multiply(new BigDecimal("1e12"));
        readable.put("price", price.setScale(2, RoundingMode.HALF_UP).toPlainString());
        readable.put("tick", raw.tick);
        // 余额换算
        BigDecimal usdt = new BigDecimal(new BigInteger(raw.usdtBalance)).divide(new BigDecimal("1e6"));
        BigDecimal weth = new BigDecimal(new BigInteger(raw.wethBalance)).divide(new BigDecimal("1e18"));
        readable.put("usdtBalance", usdt.setScale(6, RoundingMode.HALF_UP).toPlainString());
        readable.put("wethBalance", weth.setScale(8, RoundingMode.HALF_UP).toPlainString());
        // 流动性（加千位分隔符）
        readable.put("liquidity", String.format("%,d", new BigInteger(raw.liquidity)));
        return readable;
    }
}