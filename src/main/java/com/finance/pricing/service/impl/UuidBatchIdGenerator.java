package com.finance.pricing.service.impl;

import com.finance.pricing.service.BatchIdGenerator;

import java.util.UUID;

public class UuidBatchIdGenerator implements BatchIdGenerator {

    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}