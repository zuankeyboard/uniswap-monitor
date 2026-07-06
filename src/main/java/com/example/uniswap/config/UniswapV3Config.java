package com.example.uniswap.config;

public class UniswapV3Config {
    // 你要监听的池子
    public static final String POOL_ADDRESS = "0x4e68Ccd3E89f51C3074ca5072bbAC773960dFa36";
    public static final String WETH_ADDRESS = "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2";
    public static final String USDT_ADDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7";

    // ！！！关键步骤：必须换成你自己的 Infura WebSocket 地址（付费层才支持订阅）
    // public static final String WS_RPC_URL =
    // "https://eth-mainnet.g.alchemy.com/v2/Pf_TDVX0AZrk_GqRLuwqL";
    public static final String WS_RPC_URL = "wss://eth-mainnet.g.alchemy.com/v2/Pf_TDVX0AZrk_GqRLuwqL";
}