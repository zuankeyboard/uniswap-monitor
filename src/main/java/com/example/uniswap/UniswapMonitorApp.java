package com.example.uniswap;

import com.example.uniswap.config.UniswapV3Config;
import com.example.uniswap.listener.SwapEventListener;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

public class UniswapMonitorApp {
    public static void main(String[] args) throws Exception {
        // 直接传入 WebSocket URL 字符串
        WebSocketService webSocketService = new WebSocketService(UniswapV3Config.WS_RPC_URL, false);
        webSocketService.connect();

        Web3j web3j = Web3j.build(webSocketService);

        System.out.println("已连接到以太坊节点，开始监听池子: " + UniswapV3Config.POOL_ADDRESS);

        SwapEventListener.startListening(web3j);

        Thread.sleep(Long.MAX_VALUE);
        webSocketService.close();
    }
}