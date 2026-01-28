package com.finance.pricing.config;

import com.finance.pricing.service.BatchIdGenerator;
import com.finance.pricing.service.impl.UuidBatchIdGenerator;
import com.finance.pricing.service.LatestPriceService;
import com.finance.pricing.service.impl.LatestPriceServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class PricingConfiguration {

    /**
     * The bean of LatestPriceServiceImpl is created
     * in a @Configuration class to separate object creation from business logic
     * and keep the service clean, testable, and framework-agnostic.
     * @return LatestPriceService
     */
    @Bean
    LatestPriceService latestPriceService(BatchIdGenerator generator) {
        return new LatestPriceServiceImpl(generator);
    }

    @Bean
    BatchIdGenerator batchIdGenerator() {
        return new UuidBatchIdGenerator();
    }


}
