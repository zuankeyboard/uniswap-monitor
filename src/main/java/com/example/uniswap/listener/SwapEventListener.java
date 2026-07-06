package com.example.uniswap.listener;

import com.example.uniswap.config.UniswapV3Config;
import com.example.uniswap.model.SwapData;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.events.Log;
import org.web3j.protocol.websocket.events.LogNotification;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SwapEventListener {

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
        public static void startListening(Web3j web3j) {
                // 只监听池子地址，不限制主题（将接收所有事件）
                List<String> addresses = Arrays.asList(UniswapV3Config.POOL_ADDRESS);
                // List<String> topics = null; // null 表示不按主题过滤
                List<String> topics = new ArrayList<>(); // 空列表表示不限制主题

                web3j.logsNotifications(addresses, topics).subscribe(
                                notification -> {
                                        Log log = notification.getParams().getResult();
                                        String eventSignature = log.getTopics().get(0); // 事件签名
                                        if (SWAP_EVENT_SIGNATURE.equals(eventSignature)) {
                                                handleSwap(log);
                                        } else if (MINT_EVENT_SIGNATURE.equals(eventSignature)) {
                                                handleMint(log);
                                        } else if (BURN_EVENT_SIGNATURE.equals(eventSignature)) {
                                                handleBurn(log);
                                        } else {
                                                // 其他事件忽略
                                        }
                                },
                                error -> System.err.println("监听出错: " + error.getMessage()));

                System.out.println("已启动 WebSocket 订阅，监听 Swap / Mint / Burn 事件...");
        }

        // ==================== 各事件处理 ====================
        private static void handleSwap(Log log) {
                SwapData data = decodeSwap(log);
                // 添加买卖方向
                String type = determineSwapType(data.amount0, data.amount1);
                data.type = type;
                printSwapData(data);
                // System.out.println("=== 略过swap ===");
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

                double wethAmount = amount0.doubleValue() / 1e18;
                double usdtAmount = amount1.doubleValue() / 1e6;

                System.out.println("=== 添加流动性 (Mint) ===");
                System.out.println("发送者: " + sender);
                System.out.println("所有者: " + owner);
                System.out.println("价格区间 tick: " + tickLower + " - " + tickUpper);
                System.out.println("流动性数量: " + amount);
                System.out.println("存入 WETH: " + wethAmount);
                System.out.println("存入 USDT: " + usdtAmount);
                System.out.println("交易哈希: " + log.getTransactionHash());
                System.out.println("区块号: " + new BigInteger(log.getBlockNumber().substring(2), 16).toString());
                System.out.println("---");
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

                double wethAmount = amount0.doubleValue() / 1e18;
                double usdtAmount = amount1.doubleValue() / 1e6;

                System.out.println("=== 移除流动性 (Burn) ===");
                System.out.println("所有者: " + owner);
                System.out.println("价格区间 tick: " + tickLower + " - " + tickUpper);
                System.out.println("销毁流动性数量: " + amount);
                System.out.println("赎回 WETH: " + wethAmount);
                System.out.println("赎回 USDT: " + usdtAmount);
                System.out.println("交易哈希: " + log.getTransactionHash());
                System.out.println("区块号: " + new BigInteger(log.getBlockNumber().substring(2), 16).toString());
                System.out.println("---");
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

                double wethDecimal = amount0.doubleValue() / 1e18;
                double usdtDecimal = amount1.doubleValue() / 1e6;

                // 价格计算
                BigDecimal sqrtPrice = new BigDecimal(sqrtPriceX96)
                                .divide(new BigDecimal(2).pow(96), 18, RoundingMode.HALF_UP);
                BigDecimal price = sqrtPrice.multiply(sqrtPrice).multiply(new BigDecimal("1e12"));

                SwapData dataObj = new SwapData();
                dataObj.sender = sender;
                dataObj.recipient = recipient;
                dataObj.amount0 = Double.toString(wethDecimal);
                dataObj.amount1 = Double.toString(usdtDecimal);
                dataObj.sqrtPriceX96 = price.toString();
                dataObj.liquidity = liquidity.toString();
                dataObj.tick = tick.toString();
                dataObj.transactionHash = txHash;
                dataObj.blockNumber = blockNumber;
                dataObj.type = determineSwapType(dataObj.amount0, dataObj.amount1); // 设置买卖方向
                return dataObj;
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

        private static void printSwapData(SwapData data) {
                System.out.println("=== Swap 交易 ===");
                System.out.println("交易哈希: " + data.transactionHash);
                System.out.println("区块号: " + data.blockNumber);
                System.out.println("发送者: " + data.sender);
                System.out.println("接收者: " + data.recipient);
                System.out.println("方向: " + data.type);
                System.out.println("WETH 变动: " + data.amount0);
                System.out.println("USDT 变动: " + data.amount1);
                System.out.println("价格: " + data.sqrtPriceX96 + " USDT/WETH");
                System.out.println("流动性: " + data.liquidity);
                System.out.println("Tick: " + data.tick);
                System.out.println("---");
        }
}