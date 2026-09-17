package com.example.uniswap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import com.example.uniswap.config.UniswapV3Config;


@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean(destroyMethod = "shutdown")
    public Web3j web3j(UniswapV3Config config) {
        return Web3j.build(new HttpService(config.getHttpRpcUrl()));
    }
}