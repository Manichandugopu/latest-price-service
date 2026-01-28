package com.finance.pricing.service;

import com.finance.pricing.model.PriceRecord;
import com.finance.pricing.service.impl.LatestPriceServiceImpl;
import com.finance.pricing.service.impl.UuidBatchIdGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatestPriceServiceTest {
     private final LatestPriceService service =
             new LatestPriceServiceImpl(new UuidBatchIdGenerator());


    @Test
    void pricesAreInvisibleUntilBatchCompletes() {
        String batchId = service.startBatch();

        service.uploadPrices(batchId, List.of(
                new PriceRecord("etfFund", Instant.parse("2026-01-28T11:42:18.123Z"),
                        Map.of("price", 100)),
                new PriceRecord("indexFund", Instant.parse("2026-01-28T11:42:18.123Z"),
                        Map.of("price", 200)),
                new PriceRecord("commodity", Instant.parse("2026-01-28T11:42:18.123Z"),
                        Map.of("price", 300))
        ));

        // Should not be visible yet
        assertTrue(service.getLastPrices(List.of("etfFund", "indexFund", "commodity")).isEmpty());

        service.completeBatch(batchId);

        // All records should appear atomically
        assertEquals(3, service.getLastPrices(List.of("etfFund", "indexFund", "commodity")).size());
    }

    @Test
    void cancelledBatchIsCompletelyDiscarded() {
        String batchId = service.startBatch();

        service.uploadPrices(batchId, List.of(
                new PriceRecord("IBM", Instant.parse("2026-01-28T11:42:18.123Z"),
                        Map.of("price", 150)),
                new PriceRecord("ORCL", Instant.parse("2026-01-28T11:42:18.123Z"),
                        Map.of("price", 90))
        ));

        service.cancelBatch(batchId);

        // Cancelled batch must not publish any data
        assertTrue(service.getLastPrices(List.of("IBM", "ORCL")).isEmpty());
    }


    @Test
    void latestAsOfWinsWithinSameBatch() {
        String batchId = service.startBatch();

        // Same instrument appears multiple times with different asOf values
        service.uploadPrices(batchId, List.of(
                new PriceRecord("apple", Instant.parse("2026-01-28T11:42:18.123Z"),
                        Map.of("price", 100)),
                new PriceRecord("goog", Instant.parse("2026-01-28T11:42:18.123Z"),
                        Map.of("price", 120)),
                new PriceRecord("amazon", Instant.parse("2026-01-28T11:42:18.123Z"),
                        Map.of("price", 90))
        ));

        service.completeBatch(batchId);

        PriceRecord record = service.getLastPrices(List.of("goog")).get(0);
        assertEquals(120, record.payload().get("price"));
    }


    @Test
    void latestAsOfWinsAcrossMultipleBatches() {
        String batch1 = service.startBatch();
        service.uploadPrices(batch1, List.of(
                new PriceRecord("TSLA", Instant.parse("2025-12-10T11:42:18.123Z"),
                        Map.of("price", 700)),
                new PriceRecord("NFLX", Instant.parse("2025-12-10T11:42:18.123Z"),
                        Map.of("price", 400))
        ));
        service.completeBatch(batch1);

        String batch2 = service.startBatch();
        service.uploadPrices(batch2, List.of(
                new PriceRecord("TSLA", Instant.parse("2026-01-28T11:42:18.123Z"),
                        Map.of("price", 750))
        ));
        service.completeBatch(batch2);

        PriceRecord tsla = service.getLastPrices(List.of("TSLA")).get(0);
        assertEquals(750, tsla.payload().get("price"));

        // NFLX should still come from the first batch
        PriceRecord nflx = service.getLastPrices(List.of("NFLX")).get(0);
        assertEquals(400, nflx.payload().get("price"));
    }


    @Test
    void incorrectProducerCallOrderDoesNotBreakService() {
        // Upload without start
        service.uploadPrices("invalid-batch", List.of(
                new PriceRecord("FAIL", Instant.now(), Map.of("price", 1))
        ));
        // Complete non-existent batch
        service.completeBatch("invalid-batch");

        // Service should still function normally
        String batchId = service.startBatch();
        service.uploadPrices(batchId, List.of(
                new PriceRecord("SAFE", Instant.now(), Map.of("price", 999))
        ));
        service.completeBatch(batchId);

        assertEquals(1, service.getLastPrices(List.of("SAFE")).size());
    }
}
