package com.example.uniswap.listener;

import com.example.uniswap.config.UniswapV3Config;
import io.reactivex.disposables.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class SwapEventMonitor {

    private static final Logger log = LoggerFactory.getLogger(SwapEventMonitor.class);

    private final UniswapV3Config config;
    private WebSocketService webSocketService;
    private Disposable subscription;

    public SwapEventMonitor(UniswapV3Config config) {
        this.config = config;
    }

    @PostConstruct
    public void start() throws Exception {
        log.info("正在连接以太坊 WebSocket 节点: {}", config.getWsRpcUrl());
        webSocketService = new WebSocketService(config.getWsRpcUrl(), false);
        webSocketService.connect();

        Web3j web3j = Web3j.build(webSocketService);
        log.info("WebSocket 连接成功，准备创建事件订阅");
        subscription = SwapEventListener.startListening(web3j);
        log.info("链上事件监听器已启动，订阅状态: {}", subscription.isDisposed() ? "已取消" : "正常");
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
        if (webSocketService != null) {
            webSocketService.close();
        }
        log.info("链上事件监听器已停止");
    }
}
