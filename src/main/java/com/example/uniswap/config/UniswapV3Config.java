package com.example.uniswap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UniswapV3Config {

    public static final String POOL_ADDRESS = "0x4e68Ccd3E89f51C3074ca5072bbAC773960dFa36";
    public static final String WETH_ADDRESS = "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2";
    public static final String USDT_ADDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7";

    private final String httpRpcUrl;
    private final String wsRpcUrl;

    public UniswapV3Config(
            @Value("${eth.http-rpc-url}") String httpRpcUrl,
            @Value("${eth.ws-rpc-url}") String wsRpcUrl) {
        this.httpRpcUrl = httpRpcUrl;
        this.wsRpcUrl = wsRpcUrl;
    }

    public String getHttpRpcUrl() {
        return httpRpcUrl;
    }

    public String getWsRpcUrl() {
        return wsRpcUrl;
    }
}
