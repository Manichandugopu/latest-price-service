package com.finance.pricing.service.impl;

import com.finance.pricing.model.PriceRecord;
import com.finance.pricing.service.BatchIdGenerator;
import com.finance.pricing.service.LatestPriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory, thread-safe implementation.
 * Core design:
 * - Producers write into a private batch buffer
 * - Consumers always read from an immutable snapshot
 * - Snapshot is atomically replaced on batch completion
 *
 * This guarantees:
 * - No partial batch visibility
 * - Lock-free reads
 * - Correct "latest by asOf" semantics
 */
public class LatestPriceServiceImpl implements LatestPriceService {
    private static final Logger log =
            LoggerFactory.getLogger(LatestPriceServiceImpl.class);
    /**
     * Atomically published snapshot of visible prices.
     * Why AtomicReference?
     * - Allows atomic replacement of the entire map
     * - Consumers either see old snapshot or new snapshot
     * - Never a partially updated map
     * Why immutable Map?
     * - Prevents accidental modification by consumers
     * - Safe publication across threads
     */
    private final AtomicReference<Map<String, PriceRecord>> visiblePrices =
            new AtomicReference<>(Map.of());

    /**
     * Tracks batches currently being produced.
     *
     * Key   -> batchId
     * Value -> mutable batch state (not visible to consumers)
     */
    private final Map<String, BatchState> activeBatches =
            new ConcurrentHashMap<>();

    private final BatchIdGenerator batchIdGenerator;

    public LatestPriceServiceImpl(BatchIdGenerator batchIdGenerator) {
        this.batchIdGenerator = batchIdGenerator;
    }
    @Override
    public String startBatch() {
        String batchId = batchIdGenerator.nextId();
        activeBatches.put(batchId, new BatchState());
        log.info("Batch started [batchId={}]", batchId);
        return batchId;
    }

    @Override
    public void uploadPrices(String batchId, List<PriceRecord> records) {
        BatchState batch = activeBatches.get(batchId);
        //handling of incorrect producer usage
        if (batch == null || batch.completed || batch.cancelled) {
            log.warn("Upload ignored for closed batch [batchId={}]",
                    batchId);
            return;
        }
        // Multiple threads may call this concurrently
        // Keep the record with the latest asOf timestamp
        for (PriceRecord record : records) {
            batch.prices.merge(
                    record.id(),
                    record,
                    (oldVal, newVal) ->
                            newVal.asOf().isAfter(oldVal.asOf()) ? newVal : oldVal
            );
        }

        log.info("Uploaded records [batchId={}, records={}, totalInBatch={}]",
                batchId, records.size(), batch.prices.size());
    }

    @Override
    public void completeBatch(String batchId) {
        BatchState batch = activeBatches.get(batchId);
        if (batch == null || batch.completed || batch.cancelled) {
            log.warn("Complete ignored for cancelled or already completed batch [batchId={}]", batchId);
            return;
        }

        batch.completed = true;

        /**
         * Atomic snapshot update.
         *
         * Steps:
         * 1. Read current snapshot
         * 2. Create a mutable copy
         * 3. Merge batch data
         * 4. Publish new immutable snapshot atomically
         */
        visiblePrices.updateAndGet(current -> {
            Map<String, PriceRecord> next = new HashMap<>(current);
            for (PriceRecord record : batch.prices.values()) {
                next.merge(
                        record.id(),
                        record,
                        (oldVal, newVal) ->
                                newVal.asOf().isAfter(oldVal.asOf()) ? newVal : oldVal
                );
            }
            return Collections.unmodifiableMap(next);
        });
        activeBatches.remove(batchId);

        log.info("Batch completed successfully [batchId={}, publishedRecords={}]",
                batchId, batch.prices.size());
    }

    @Override
    public void cancelBatch(String batchId) {
        // Removing batch discards all staged data
        BatchState batch = activeBatches.remove(batchId);
        if (batch != null) {
            batch.cancelled = true;
            log.info("Batch cancelled [batchId={}, discardedRecords={}]",
                    batchId, batch.prices.size());
        }
    }

    @Override
    public List<PriceRecord> getLastPrices(List<String> ids) {
        Map<String, PriceRecord> snapshot = visiblePrices.get();
        List<PriceRecord> result = new ArrayList<>();

        for (String id : ids) {
            PriceRecord record = snapshot.get(id);
            if (record != null) {
                result.add(record);
            }
        }
        log.info("Consumer read [requestedIds={}, returnedRecords={}]",
                ids.size(), result.size());
        return result;
    }

    /**
     * Mutable per-batch write buffer.
     * Design:
     * - ConcurrentHashMap allows parallel uploads
     * - Volatile flags are enough for lifecycle state
     */
    private static class BatchState {
        final Map<String, PriceRecord> prices = new ConcurrentHashMap<>();
        volatile boolean completed;
        volatile boolean cancelled;
    }

}
