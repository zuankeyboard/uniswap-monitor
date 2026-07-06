package com.example.uniswap.service;

import com.example.uniswap.config.UniswapV3Config;
import com.example.uniswap.listener.SwapEventListener; // [改动] 导入监听类以使用其静态方法和常量
import com.example.uniswap.model.PoolState;
import com.example.uniswap.model.SwapData;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log; // [改动] 使用 core 包下的 Log，而非 websocket 包

import org.springframework.stereotype.Service; // 新增导入

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class PoolQueryService {

    private final Web3j web3j;

    public PoolQueryService(Web3j web3j) {
        this.web3j = web3j;
    }

    /**
     * 获取资金池的完整状态
     */
    public PoolState getPoolState() throws Exception {
        PoolState state = new PoolState();
        state.sqrtPriceX96 = getSqrtPriceX96();
        state.tick = getTick();
        state.liquidity = getLiquidity();
        state.usdtBalance = getTokenBalance(UniswapV3Config.USDT_ADDRESS);
        state.wethBalance = getTokenBalance(UniswapV3Config.WETH_ADDRESS);
        return state;
    }

    // ==================== 私有查询方法 ====================

    /**
     * 查询 slot0 中的 sqrtPriceX96
     */
    private String getSqrtPriceX96() throws Exception {
        Function function = new Function(
                "slot0",
                Arrays.asList(),
                Arrays.asList(
                        new TypeReference<Uint160>() {
                        },
                        new TypeReference<Int24>() {
                        },
                        new TypeReference<Uint16>() {
                        },
                        new TypeReference<Uint16>() {
                        },
                        new TypeReference<Uint16>() {
                        },
                        new TypeReference<Uint8>() {
                        },
                        new TypeReference<Bool>() {
                        }));

        List<Type> result = callFunction(function);
        return ((Uint160) result.get(0)).getValue().toString();
    }

    /**
     * 查询 slot0 中的 tick
     */
    private String getTick() throws Exception {
        Function function = new Function(
                "slot0",
                Arrays.asList(),
                Arrays.asList(
                        new TypeReference<Uint160>() {
                        },
                        new TypeReference<Int24>() {
                        },
                        new TypeReference<Uint16>() {
                        },
                        new TypeReference<Uint16>() {
                        },
                        new TypeReference<Uint16>() {
                        },
                        new TypeReference<Uint8>() {
                        },
                        new TypeReference<Bool>() {
                        }));

        List<Type> result = callFunction(function);
        return ((Int24) result.get(1)).getValue().toString();
    }

    /**
     * 查询当前活跃流动性 (liquidity)
     */
    private String getLiquidity() throws Exception {
        Function function = new Function(
                "liquidity",
                Arrays.asList(),
                Arrays.asList(new TypeReference<Uint128>() {
                }));

        List<Type> result = callFunction(function);
        return ((Uint128) result.get(0)).getValue().toString();
    }

    /**
     * 查询指定代币在池子中的余额 (ERC20 balanceOf)
     */
    private String getTokenBalance(String tokenAddress) throws Exception {
        Function function = new Function(
                "balanceOf",
                Arrays.asList(new Address(UniswapV3Config.POOL_ADDRESS)),
                Arrays.asList(new TypeReference<Uint256>() {
                }));

        List<Type> result = callFunction(function, tokenAddress);
        return ((Uint256) result.get(0)).getValue().toString();
    }

    // ==================== 通用调用方法 ====================

    /**
     * 调用池子的合约方法（无参）
     */
    private List<Type> callFunction(Function function) throws Exception {
        return callFunction(function, UniswapV3Config.POOL_ADDRESS);
    }

    /**
     * 调用任意合约方法（可指定合约地址）
     */
    private List<Type> callFunction(Function function, String contractAddress) throws Exception {
        String encodedFunction = FunctionEncoder.encode(function);
        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST).send();

        if (response.hasError()) {
            throw new RuntimeException("查询失败: " + response.getError().getMessage());
        }

        return FunctionReturnDecoder.decode(
                response.getValue(),
                function.getOutputParameters());
    }

    // ==================== 新增：历史交易查询 ====================

    /**
     * 查询指定区块范围内的 Swap 历史交易记录
     * 
     * @param fromBlock 起始区块号（十六进制字符串，如 "0x1840000" 或十进制 BigInteger）
     * @param toBlock   结束区块号
     * @return SwapData 列表
     */
    public List<SwapData> getHistoricalSwaps(BigInteger fromBlock, BigInteger toBlock) throws Exception {
        EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(fromBlock),
                DefaultBlockParameter.valueOf(toBlock),
                UniswapV3Config.POOL_ADDRESS);
        filter.addSingleTopic(SwapEventListener.SWAP_EVENT_SIGNATURE);

        EthLog ethLog = web3j.ethGetLogs(filter).send();
        List<SwapData> swaps = new ArrayList<>();

        // 遍历 EthLog.LogResult
        for (EthLog.LogResult<?> logResult : ethLog.getLogs()) {
            // 使用 get() 方法获取实际的 Log 对象（类型为 Object，实际是 core 包的 Log）
            Object rawLog = logResult.get();
            if (rawLog instanceof org.web3j.protocol.core.methods.response.Log) {
                org.web3j.protocol.core.methods.response.Log log = (org.web3j.protocol.core.methods.response.Log) rawLog;
                SwapData data = SwapEventListener.decode(log); // 调用重载1
                swaps.add(data);
            }
        }
        return swaps;
    }
}