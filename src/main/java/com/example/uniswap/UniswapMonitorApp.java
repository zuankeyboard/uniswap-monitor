package com.example.uniswap;

import com.example.uniswap.config.UniswapV3Config;
import com.example.uniswap.listener.SwapEventListener;
import org.web3j.protocol.Web3j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.websocket.WebSocketService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class UniswapMonitorApp {

    private static final Logger log = LoggerFactory.getLogger(UniswapMonitorApp.class);

    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = UniswapMonitorApp.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IOException("找不到 application.properties 配置文件");
            }
            properties.load(input);
        }
        WebSocketService webSocketService = new WebSocketService(
                properties.getProperty("eth.ws-rpc-url"), false);
        try {
            webSocketService.connect();
            Web3j web3j = Web3j.build(webSocketService);
            log.info("已连接到以太坊节点，开始监听池子: {}", UniswapV3Config.POOL_ADDRESS);
            SwapEventListener.startListening(web3j);
            Thread.currentThread().join();
        } finally {
            webSocketService.close();
        }
    }
}