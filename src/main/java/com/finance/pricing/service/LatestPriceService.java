package com.finance.pricing.service;

import com.finance.pricing.model.PriceRecord;

import java.util.List;

/**
 * Service API shared by producers and consumers.
 *
 * Producers:
 * - startBatch
 * - uploadPrices (parallel, chunked)
 * - complete or cancel batch
 *
 * Consumers:
 * - getLastPrices
 */
public interface LatestPriceService {

    String startBatch();

    void uploadPrices(String batchId, List<PriceRecord> records);

    void completeBatch(String batchId);

    void cancelBatch(String batchId);

    List<PriceRecord> getLastPrices(List<String> ids);
}
