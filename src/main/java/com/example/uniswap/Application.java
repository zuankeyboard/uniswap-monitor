package com.example.uniswap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public Web3j web3j() {
        // 使用 HTTP 连接（稳定且免费层可用）
        return Web3j.build(new HttpService("https://eth-mainnet.g.alchemy.com/v2/Pf_TDVX0AZrk_GqRLuwqL"));
    }
}