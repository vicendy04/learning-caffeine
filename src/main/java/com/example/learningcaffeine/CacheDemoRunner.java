package com.example.learningcaffeine;

import com.example.learningcaffeine.model.DataObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * <a href="https://www.baeldung.com/java-caching-caffeine">...</a>
 * continue: <a href="https://www.baeldung.com/java-caching-caffeine#eviction-of-values">...</a>
 */
@Slf4j
@Component
public class CacheDemoRunner implements CommandLineRunner {
    private static final String DEMO_LINE = "=".repeat(60);

    @Override
    public void run(String... args) {
        demoManualCacheStrategy();
        log.info("End!!!!!!!!!!");
        demoLoadingCacheStrategy();
        log.info("End!!!!!!!!!!");
        demoCacheAsideWithManyKeys();
    }

    /**
     * Ref: <a href="https://github.com/ben-manes/caffeine/discussions/588">...</a>
     */
    private void demoCacheAsideWithManyKeys() {
        LoadingCache<String, Object> cache = Caffeine.newBuilder().build(new CacheLoader<>() {
            @Override
            public Object load(String id) {
                System.out.println("Fetching single ID from DB: " + id);
                return fetchFromDB(id);
            }

            @Override
            public Map<String, Object> loadAll(Set<? extends String> ids) {
                System.out.println("Fetching batch IDs from DB: " + ids);
                return fetchBatchFromDB(ids);
            }
        });
        cache.put("UUID_2", "DB_2");
        System.out.println("get all first time");
        Map<String, Object> results = cache.getAll(Set.of("UUID_1", "UUID_2", "UUID_3"));
        System.out.println("Result: " + results);
        System.out.println("get all second time");
        Map<String, Object> results1 = cache.getAll(Set.of("UUID_1", "UUID_2", "UUID_3"));
        System.out.println("Result: " + results1);
    }

    private static Object fetchFromDB(String id) {
        return "DB_" + id;
    }

    private static Map<String, Object> fetchBatchFromDB(Set<? extends String> ids) {
        Map<String, Object> dbResults = new HashMap<>();
        for (String id : ids) {
            dbResults.put(id, "DB_" + id);
        }
        return dbResults;
    }

    private void demoManualCacheStrategy() {
        logDemoHeader("Manual Cache Strategy Demo");

        Cache<String, DataObject> manualCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();
        String temp1 =
                """

                        Cache<String, DataObject> cache = Caffeine.newBuilder()
                        		.expireAfterWrite(1, TimeUnit.MINUTES)
                        		.maximumSize(100)
                        		.build();
                        		""";
        log.info(temp1);

        final String key = "A";

        logStep("Initial empty cache check");
        DataObject value = manualCache.getIfPresent(key);
        assertWithMessage("Cache should be empty initially", value == null);

        logStep("Add data to cache manually");
        DataObject fakeData = new DataObject("Fake Data");
        manualCache.put(key, fakeData);

        value = manualCache.getIfPresent(key);
        assertWithMessage("Cache should contain manually added data", value != null && value.equals(fakeData));

        logStep("Invalidate cache entry");
        manualCache.invalidate(key);
        value = manualCache.getIfPresent(key);
        assertWithMessage("Cache should be empty after invalidation", value == null);

        logStep("Use get() with fallback function");
        String expectedData = String.format("Data for %s", key);
        // fallback value if the key is not present in the cache,
        // which would be inserted in the cache after computation
        value = manualCache.get(key, k -> DataObject.get(expectedData));

        assertWithMessage(
                "Cache should return computed value", value != null && value.equals(DataObject.get(expectedData)));
        assertWithMessage("Computed value should be in cache now", manualCache.getIfPresent(key) != null);
    }

    private void demoLoadingCacheStrategy() {
        logDemoHeader("Loading Cache Strategy Demo");

        LoadingCache<String, DataObject> loadingCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build(k -> DataObject.get("Data for " + k));
        String temp2 =
                """

                        LoadingCache<String, DataObject> loadingCache = Caffeine.newBuilder()
                        		.maximumSize(100)
                        		.expireAfterWrite(1, TimeUnit.MINUTES)
                        		.build(k -> DataObject.get("Data for " + k)); // loader
                        				""";
        log.info(temp2);

        final List<String> keys = Arrays.asList("A", "B", "C");

        logStep("Get single value with automatic loading");
        String expectedData = String.format("Data for %s", keys.get(0));
        // similar to the get method of the manual strategy
        DataObject valueA = loadingCache.get(keys.get(0));
        assertWithMessage("Should load data for single key", valueA.equals(DataObject.get(expectedData)));

        logStep("Batch loading with getAll()");
        Map<String, DataObject> loadedData = loadingCache.getAll(keys);

        assertWithMessage(
                "Should load data for all keys",
                loadedData.size() == keys.size() && loadedData.values().stream().allMatch(Objects::nonNull));

        logStep("Verify cached data");
        keys.forEach(key -> {
            DataObject cachedValue = loadingCache.getIfPresent(key);
            assertWithMessage(
                    "Data should be in cache after loading",
                    cachedValue != null && cachedValue.equals(DataObject.get("Data for " + key)));
        });
    }

    // region Helper Methods
    private void logDemoHeader(String title) {
        System.out.println("\n" + DEMO_LINE);
        System.out.println(">>> " + title);
        System.out.println(DEMO_LINE);
    }

    private void logStep(String message) {
        System.out.println("\n[Step] " + message);
    }

    private void assertWithMessage(String message, boolean condition) {
        System.out.printf("[Assert] %s - %s%n", condition ? "PASS" : "FAIL", message);
        if (!condition) throw new AssertionError(message);
    }
}
