package com.xfusion.bidaggregator;

import com.xfusion.bidaggregator.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class BidAggregatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(BidAggregatorApplication.class, args);
    }
}
