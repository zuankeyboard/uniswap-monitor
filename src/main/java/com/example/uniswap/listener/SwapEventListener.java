package com.example.uniswap.listener;

import com.example.uniswap.config.UniswapV3Config;
import com.example.uniswap.model.SwapData;
import com.example.uniswap.service.SupabaseEventPersistence;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.events.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SwapEventListener {

        private static final Logger log = LoggerFactory.getLogger(SwapEventListener.class);
        private static SupabaseEventPersistence persistence;

        public SwapEventListener(SupabaseEventPersistence persistence) {
                SwapEventListener.persistence = persistence;
        }

        // ==================== 事件定义 ====================
        // Swap 事件
        private static final Event SWAP_EVENT = new Event("Swap",
                        Arrays.asList(
                                        new TypeReference<Address>(true) {
                                        },
                                        new TypeReference<Address>(true) {
                                        },
                                        new TypeReference<Int256>() {
                                        },
                                        new TypeReference<Int256>() {
                                        },
                                        new TypeReference<Uint160>() {
                                        },
                                        new TypeReference<Uint128>() {
                                        },
                                        new TypeReference<Int24>() {
                                        }));
        public static final String SWAP_EVENT_SIGNATURE = EventEncoder.encode(SWAP_EVENT);

        // Mint 事件
        private static final Event MINT_EVENT = new Event("Mint",
                        Arrays.asList(
                                        new TypeReference<Address>() {
                                        }, // sender (non-indexed)
                                        new TypeReference<Address>(true) {
                                        }, // owner (indexed)
                                        new TypeReference<Int24>(true) {
                                        }, // tickLower (indexed)
                                        new TypeReference<Int24>(true) {
                                        }, // tickUpper (indexed)
                                        new TypeReference<Uint128>() {
                                        }, // amount (non-indexed)
                                        new TypeReference<Uint256>() {
                                        }, // amount0 (non-indexed)
                                        new TypeReference<Uint256>() {
                                        } // amount1 (non-indexed)
                        ));
        public static final String MINT_EVENT_SIGNATURE = EventEncoder.encode(MINT_EVENT);

        // Burn 事件
        private static final Event BURN_EVENT = new Event("Burn",
                        Arrays.asList(
                                        new TypeReference<Address>(true) {
                                        }, // owner
                                        new TypeReference<Int24>(true) {
                                        }, // tickLower
                                        new TypeReference<Int24>(true) {
                                        }, // tickUpper
                                        new TypeReference<Uint128>() {
                                        }, // amount
                                        new TypeReference<Uint256>() {
                                        }, // amount0
                                        new TypeReference<Uint256>() {
                                        })); // amount1
        public static final String BURN_EVENT_SIGNATURE = EventEncoder.encode(BURN_EVENT);

        // ==================== 启动监听 ====================
        public static Disposable startListening(Web3j web3j) {
                List<String> addresses = Arrays.asList(UniswapV3Config.POOL_ADDRESS);
                CompositeDisposable subscriptions = new CompositeDisposable();
                subscriptions.add(subscribe(web3j, addresses, SWAP_EVENT_SIGNATURE, SwapEventListener::handleSwap));
                subscriptions.add(subscribe(web3j, addresses, MINT_EVENT_SIGNATURE, SwapEventListener::handleMint));
                subscriptions.add(subscribe(web3j, addresses, BURN_EVENT_SIGNATURE, SwapEventListener::handleBurn));

                log.info("WebSocket 订阅已启动，监听池子 {} 的 Swap、Mint、Burn 事件", UniswapV3Config.POOL_ADDRESS);
                return subscriptions;
        }

        private static Disposable subscribe(Web3j web3j, List<String> addresses, String signature,
                        java.util.function.Consumer<Log> handler) {
                return web3j.logsNotifications(addresses, Arrays.asList(signature)).subscribe(
                                notification -> {
                                        Log eventLog = notification.getParams().getResult();
                                        log.info("收到链上事件: signature={}, topics={}", signature,
                                                        eventLog.getTopics().size());
                                        try {
                                                handler.accept(eventLog);
                                        } catch (RuntimeException exception) {
                                                log.error("事件解码失败: signature={}, topics={}, data={}",
                                                                signature, eventLog.getTopics(), eventLog.getData(), exception);
                                        }
                                },
                                error -> log.error("事件订阅失败: signature={}", signature, error));
        }

        // ==================== 各事件处理 ====================
        private static void handleSwap(Log log) {
                SwapData data = decodeSwap(log);
                // 添加买卖方向
                String type = determineSwapType(data.amount0, data.amount1);
                data.type = type;

                SwapEventListener.log.info(
                                "[SWAP] 交易哈希: {} | 区块号: {} | 发送者: {} | 接收者: {} | 方向: {} | WETH: {} | USDT: {} | 价格: {} USDT/WETH | 流动性: {} | Tick: {}",
                                data.transactionHash, data.blockNumber, data.sender, data.recipient, data.type,
                                data.amount0, data.amount1, data.sqrtPriceX96, data.liquidity, data.tick);
                persistence.saveSwap(data);
        }

        private static void handleMint(Log log) {
                List<String> topics = log.getTopics();
                String owner = "0x" + topics.get(1).substring(topics.get(1).length() - 40);
                String tickLower = new BigInteger(topics.get(2).substring(2), 16).toString();
                String tickUpper = new BigInteger(topics.get(3).substring(2), 16).toString();

                List<Type> nonIndexed = FunctionReturnDecoder.decode(
                                log.getData(),
                                MINT_EVENT.getNonIndexedParameters());
                // nonIndexed: [sender, amount, amount0, amount1]
                String sender = ((Address) nonIndexed.get(0)).getValue();
                BigInteger amount = (BigInteger) ((Uint128) nonIndexed.get(1)).getValue();
                BigInteger amount0 = (BigInteger) ((Uint256) nonIndexed.get(2)).getValue();
                BigInteger amount1 = (BigInteger) ((Uint256) nonIndexed.get(3)).getValue();

                BigDecimal wethAmount = new BigDecimal(amount0).movePointLeft(18);
                BigDecimal usdtAmount = new BigDecimal(amount1).movePointLeft(6);

                SwapEventListener.log.info(
                                "[MINT] 发送者: {} | 所有者: {} | Tick范围: {} - {} | 流动性: {} | WETH: {} | USDT: {} | 交易哈希: {} | 区块号: {}",
                                sender, owner, tickLower, tickUpper, amount, wethAmount.toPlainString(),
                                usdtAmount.toPlainString(), log.getTransactionHash(),
                                parseHexNumber(log.getBlockNumber()));
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("sender", sender);
                payload.put("owner", owner);
                payload.put("tick_lower", tickLower);
                payload.put("tick_upper", tickUpper);
                payload.put("liquidity", amount.toString());
                payload.put("amount0", amount0.toString());
                payload.put("amount1", amount1.toString());
                persistence.saveRawEvent("MINT", log.getTransactionHash(), parseHexNumber(log.getBlockNumber()), payload);
        }

        private static void handleBurn(Log log) {
                List<String> topics = log.getTopics();
                String owner = "0x" + topics.get(1).substring(topics.get(1).length() - 40);
                String tickLower = new BigInteger(topics.get(2).substring(2), 16).toString();
                String tickUpper = new BigInteger(topics.get(3).substring(2), 16).toString();

                List<Type> nonIndexed = FunctionReturnDecoder.decode(
                                log.getData(),
                                BURN_EVENT.getNonIndexedParameters());
                // nonIndexed: [amount, amount0, amount1]
                BigInteger amount = (BigInteger) ((Uint128) nonIndexed.get(0)).getValue();
                BigInteger amount0 = (BigInteger) ((Uint256) nonIndexed.get(1)).getValue();
                BigInteger amount1 = (BigInteger) ((Uint256) nonIndexed.get(2)).getValue();

                BigDecimal wethAmount = new BigDecimal(amount0).movePointLeft(18);
                BigDecimal usdtAmount = new BigDecimal(amount1).movePointLeft(6);

                SwapEventListener.log.info(
                                "[BURN] 所有者: {} | Tick范围: {} - {} | 流动性: {} | WETH: {} | USDT: {} | 交易哈希: {} | 区块号: {}",
                                owner, tickLower, tickUpper, amount, wethAmount.toPlainString(),
                                usdtAmount.toPlainString(), log.getTransactionHash(),
                                parseHexNumber(log.getBlockNumber()));
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("owner", owner);
                payload.put("tick_lower", tickLower);
                payload.put("tick_upper", tickUpper);
                payload.put("liquidity", amount.toString());
                payload.put("amount0", amount0.toString());
                payload.put("amount1", amount1.toString());
                persistence.saveRawEvent("BURN", log.getTransactionHash(), parseHexNumber(log.getBlockNumber()), payload);
        }

        // ==================== Swap 解码 ====================

        /**
         * 公共静态方法，供历史查询（core.Log）调用
         */
        public static SwapData decode(org.web3j.protocol.core.methods.response.Log log) {
                String blockNumber = log.getBlockNumber().toString();
                return decodeSwapFromFields(log.getTopics(), log.getData(), log.getTransactionHash(), blockNumber);
        }

        /**
         * 私有方法，供实时监听（websocket.Log）调用
         */
        private static SwapData decodeSwap(org.web3j.protocol.websocket.events.Log log) {
                String blockNumber = new BigInteger(log.getBlockNumber().substring(2), 16).toString();
                return decodeSwapFromFields(log.getTopics(), log.getData(), log.getTransactionHash(), blockNumber);
        }

        /**
         * 核心解码逻辑（不依赖具体 Log 类型）
         */
        private static SwapData decodeSwapFromFields(List<String> topics, String data, String txHash,
                        String blockNumber) {
                String sender = "0x" + topics.get(1).substring(topics.get(1).length() - 40);
                String recipient = "0x" + topics.get(2).substring(topics.get(2).length() - 40);

                List<Type> nonIndexed = FunctionReturnDecoder.decode(
                                data,
                                SWAP_EVENT.getNonIndexedParameters());

                int idx = 0;
                BigInteger amount0 = (BigInteger) ((Int256) nonIndexed.get(idx++)).getValue();
                BigInteger amount1 = (BigInteger) ((Int256) nonIndexed.get(idx++)).getValue();
                BigInteger sqrtPriceX96 = (BigInteger) ((Uint160) nonIndexed.get(idx++)).getValue();
                BigInteger liquidity = (BigInteger) ((Uint128) nonIndexed.get(idx++)).getValue();
                BigInteger tick = (BigInteger) ((Int24) nonIndexed.get(idx++)).getValue();

                BigDecimal wethDecimal = new BigDecimal(amount0).movePointLeft(18);
                BigDecimal usdtDecimal = new BigDecimal(amount1).movePointLeft(6);

                // 价格计算
                BigDecimal sqrtPrice = new BigDecimal(sqrtPriceX96)
                                .divide(new BigDecimal(2).pow(96), 18, RoundingMode.HALF_UP);
                BigDecimal price = sqrtPrice.multiply(sqrtPrice).multiply(new BigDecimal("1e12"));

                SwapData dataObj = new SwapData();
                dataObj.sender = sender;
                dataObj.recipient = recipient;
                dataObj.amount0 = wethDecimal.toPlainString();
                dataObj.amount1 = usdtDecimal.toPlainString();
                dataObj.sqrtPriceX96 = price.toString();
                dataObj.liquidity = liquidity.toString();
                dataObj.tick = tick.toString();
                dataObj.transactionHash = txHash;
                dataObj.blockNumber = blockNumber;
                dataObj.type = determineSwapType(dataObj.amount0, dataObj.amount1); // 设置买卖方向
                return dataObj;
        }

        private static String parseHexNumber(String value) {
                return new BigInteger(value.substring(2), 16).toString();
        }

        private static String determineSwapType(String amount0Str, String amount1Str) {
                // amount0 对应 WETH，amount1 对应 USDT
                double a0 = Double.parseDouble(amount0Str);
                double a1 = Double.parseDouble(amount1Str);

                if (a0 > 0 && a1 < 0) {
                        return "卖出 WETH，买入 USDT"; // 池子流入 WETH，流出 USDT
                } else if (a0 < 0 && a1 > 0) {
                        return "卖出 USDT，买入 WETH";
                } else {
                        return "未知方向";
                }
        }
}