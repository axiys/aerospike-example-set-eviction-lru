package com.aerospike.example;

import com.aerospike.client.*;
import com.aerospike.client.Record;
import com.aerospike.client.cluster.Node;
import com.aerospike.client.policy.WritePolicy;
import org.apache.commons.cli.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    private static int DEFAULT_NUMBER_OF_THREADS = 2 * 16; /// Use: lscpu for number of CPUs eg. 16
    private static int DEFAULT_NUMBER_OF_OPERATIONS_PER_THREAD = 10;

    // See namespace configuration lru_test
    private static int AEROSPIKE_CONF_LRU_TTL = 10;     // must match default-ttl in aerospike.conf
    private static int AEROSPIKE_CONF_NSUP_PERIOD = 1; // must match nsup-period

    private static int TEST_GOAL_MAX_DATA_SET_SIZE = 800;
    private static int TEST_INITIAL_DATA_SET_SIZE = 1000;
    private static int TEST_GROWTH_DATA_SET_SIZE = 200;

    private static String TEST_NAMESPACE_NAME = "lru_test";
    private static String TEST_SET_NAME = "mycache";
    private static String TEST_BIN_NAME = "bin1";

    private static Random random = new Random(LocalDateTime.now().getNano() * LocalDateTime.now().getSecond());

    private static AerospikeClient createAerospikeClient() {
        return new AerospikeClient(null, new Host("127.0.0.1", 4000));
    }

    private static class CacheItemUsageTracking {
        private Key key;
        private String recordId;
        private AtomicInteger hits;

        CacheItemUsageTracking(Key key, String id) {
            this.key = key;
            this.recordId = id;
            this.hits = new AtomicInteger();
        }

        public void hit() {
            hits.incrementAndGet();
        }

        public Key getKey() {
            return key;
        }

        public String getRecordId() {
            return recordId;
        }

        public int getHits() {
            return hits.get();
        }
    }


    public static void main(String[] args) {

        AerospikeClient client = null;

        try {
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Parse command line arguments
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            Options options = new Options();

            Option numberOfOperationsPerThread = new Option("c", "operations", true, "Number of operations to carry out per thread");
            numberOfOperationsPerThread.setRequired(false);
            options.addOption(numberOfOperationsPerThread);

            Option numberOfThreads = new Option("z", "threads", true, "Set the number of threads the client will use to generate load");
            numberOfThreads.setRequired(false);
            options.addOption(numberOfThreads);

            CommandLineParser parser = new DefaultParser();
            HelpFormatter formatter = new HelpFormatter();
            CommandLine cmd = null;

            try {
                cmd = parser.parse(options, args);
            } catch (ParseException e) {
                System.out.println(e.getMessage());
                formatter.printHelp("aerospike-example-set-eviction-lru", options);
                System.exit(1);
            }

            int threadCount = cmd != null ? Integer.parseInt(cmd.getOptionValue("threads", String.valueOf(DEFAULT_NUMBER_OF_THREADS))) : DEFAULT_NUMBER_OF_THREADS;
            int operationsPerThreadCount = cmd != null ? Integer.parseInt(cmd.getOptionValue("operations", String.valueOf(DEFAULT_NUMBER_OF_OPERATIONS_PER_THREAD))) : DEFAULT_NUMBER_OF_OPERATIONS_PER_THREAD;
            //   int lruTTL_sec = AEROSPIKE_CONF_LRU_TTL;

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Verify: empty cache to start off with
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            client = createAerospikeClient();

            long existingObjectCount = getObjectCountInSet(client, TEST_NAMESPACE_NAME, TEST_SET_NAME);
            if (existingObjectCount > 0) {
                throw new Exception("Cache should be empty before starting. Either wait until existing items have expired or your have misconfigured your TTL and NSUP for the namespace or record");
            }

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Generate the set of keys that will be used for LRU cache testing
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            System.out.print("\nCreating random records to test in LRU cache ... ");

            // client = createAerospikeClient();

            //Load up and initially large volume of objects
             RandomObjectGeneratorPolicy.RandomObjectGenerator.generate(client, TEST_NAMESPACE_NAME, TEST_SET_NAME, TEST_BIN_NAME, AEROSPIKE_CONF_LRU_TTL, TEST_INITIAL_DATA_SET_SIZE);
//            WritePolicy writePolicy = new WritePolicy();
//            writePolicy.expiration = AEROSPIKE_CONF_LRU_TTL;
//            String recordIdPrefix = "record_id-" + UUID.randomUUID() + "-";
//            for (int i = 0; i < TEST_DATA_SET_SIZE; i++) {
//                String recordId = recordIdPrefix + i;
//                Key key = new Key(TEST_NAMESPACE_NAME, TEST_SET_NAME, recordId);
//                client.put(writePolicy, key, new Bin(TEST_BIN_NAME, generateRandomString(random)));
//
//                // This is for internal tracking for the test
//                // - We will hand over this tracking information to threads for them to choose a random key they wish to
//                //   keep alive. Threads may be choosing the same key, which is fine since this would happen in real life.
//                // - Everytime a key is used, the key's tracking counter will increase
//                // - At the end of the test, we check if the last used keys from the cache are still in the cache. Also, if
//                //   the keys that weren't used have expired due to the automatic expiry of records by Aerospike server (NSUP)
//                cacheItemUsageTrackers.add(new CacheItemUsageTracking(key, recordId));
//
//                // Launch at random times
//                int randomTime_sec = random.nextInt(lruTTL_sec );
//                Thread.sleep(randomTime_sec);
//                //System.out.print(".");
//            }
//            client.close();

            //   System.out.println("Successful");
            // This is for internal tracking for the test
            // - We will hand over this tracking information to threads for them to choose a random key they wish to
            //   keep alive. Threads may be choosing the same key, which is fine since this would happen in real life.
            // - Everytime a key is used, the key's tracking counter will increase
            // - At the end of the test, we check if the last used keys from the cache are still in the cache. Also, if
            //   the keys that weren't used have expired due to the automatic ex
            // This is for internal tracking for the test
            // - We will hand over this tracking information to threads for them to choose a random key they wish to
            //   keep alive. Threads may be choosing the same key, which is fine since this would happen in real life.
            // - Everytime a key is used, the key's tracking counter will increase
            // - At the end of the test, we check if the last used keys from the cache are still in the cache. Also, if
            //   the keys that weren't used have expired due to the automatic ex
            // Wait for Aerospike server to sweep and clear out records
//            Thread.sleep(1000 * (AEROSPIKE_CONF_NSUP_PERIOD * 10));
            //   client = createAerospikeClient();
//            existingObjectCount = getObjectCountInSet(client, TEST_NAMESPACE_NAME, TEST_SET_NAME);
//            System.out.println(existingObjectCount);
//            removeObjectFirstN(client, 5);

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Run test
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            System.out.println("\nRunning tests: threads=" + threadCount + ", operations per thread=" + operationsPerThreadCount + ", ttl=" + AEROSPIKE_CONF_LRU_TTL + " seconds ... ");

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Monitor number of objects in the various histogram TTL buckets
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            AtomicBoolean cancelMonitor = new AtomicBoolean();
            new Thread(new ManageMaxObjectsInLRUCachePolicy(cancelMonitor, createAerospikeClient(), TEST_NAMESPACE_NAME, TEST_SET_NAME, AEROSPIKE_CONF_LRU_TTL, TEST_GOAL_MAX_DATA_SET_SIZE)).start();

            // Randomly add new objects so we can test max LRU object size
            AtomicBoolean cancelGenerator = new AtomicBoolean();
            new Thread(new RandomObjectGeneratorPolicy(cancelGenerator, createAerospikeClient(), TEST_NAMESPACE_NAME, TEST_SET_NAME, TEST_BIN_NAME, AEROSPIKE_CONF_LRU_TTL, TEST_GROWTH_DATA_SET_SIZE)).start();

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Launch object creators with varying random TTL
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            ArrayList<CacheItemUsageTracking> cacheItemUsageTrackers = new ArrayList<>();

            ExecutorService es = Executors.newCachedThreadPool();

            int n = threadCount;
            while (n-- > 0) {

                // This is for internal tracking for the test
                // - We will hand over this tracking information to threads for them to choose a random key they wish to
                //   keep alive. Threads may be choosing the same key, which is fine since this would happen in real life.
                // - Everytime a key is used, the key's tracking counter will increase
                // - At the end of the test, we check if the last used keys from the cache are still in the cache. Also, if
                //   the keys that weren't used have expired due to the automatic expiry of records by Aerospike server (NSUP)


                // Randomly generate a record for each worker thread to keep alive
                List<String> generatedRecordIds = RandomObjectGeneratorPolicy.RandomObjectGenerator.generate(client, TEST_NAMESPACE_NAME, TEST_SET_NAME, TEST_BIN_NAME, AEROSPIKE_CONF_LRU_TTL, 1);
                String recordId = generatedRecordIds.get(0);
                Key key = new Key(TEST_NAMESPACE_NAME, TEST_SET_NAME, recordId);
                CacheItemUsageTracking randomCachedItemTracker = new CacheItemUsageTracking(key, recordId);
                cacheItemUsageTrackers.add(new CacheItemUsageTracking(key, recordId));

                es.execute(new BenchmarkWorker(randomCachedItemTracker, operationsPerThreadCount, AEROSPIKE_CONF_LRU_TTL, random));
            }

            es.shutdown();

            // Wait for termination
            try {
                boolean finished = es.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            cancelMonitor.set(true);
            cancelGenerator.set(true);
            System.out.println("\n");

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Verify - some cache items still around before their TTL
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            System.out.print("\nVerifying: some cache items are still around before their TTL ... ");

            long finalObjectCount = getObjectCountInSet(client, TEST_NAMESPACE_NAME, TEST_SET_NAME);
            if (finalObjectCount == 0) {
                throw new Exception("Cache should have some items");
            }
            System.out.println("Successful");

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Verify - check that the records we expect to be alive, are alive
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            System.out.print("Verifying: expected records have been kept alive... ");

            boolean failed = false;
            client = createAerospikeClient();
            for (CacheItemUsageTracking cachedItem : cacheItemUsageTrackers) {
                // Fetch the record
                Record r = client.get(null, cachedItem.getKey());

                boolean recordExists = r != null;
                boolean shouldExist = cachedItem.getHits() > 0;

                if (!shouldExist && recordExists) {
                    System.out.println("ERROR: Record should not be in cache: " + cachedItem.getRecordId() + ", test hits=" + cachedItem.getHits());
                    failed = true;
                }
            }

            System.out.println(failed ? "Failed" : "Successful");
//            showObjectsHistograms(client);

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Verify - wait for TTL, all cache records should have disappeared
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            System.out.print("Verifying: wait for just after TTL, all cache records should have disappeared ... ");

            // Wait for Aerospike server to sweep and clear out records
            Thread.sleep(1000 * (AEROSPIKE_CONF_LRU_TTL + AEROSPIKE_CONF_NSUP_PERIOD * 2));

            failed = false;
            client = createAerospikeClient();
            for (CacheItemUsageTracking cachedItem : cacheItemUsageTrackers) {
                // Fetch the record
                Record r = client.get(null, cachedItem.getKey());

                boolean recordExists = r != null;

                if (recordExists) {
                    System.out.println("ERROR: Record should not be in cache: " + cachedItem.getRecordId() + ", test hits=" + cachedItem.getHits());
                    failed = true;
                }
            }
            System.out.println(failed ? "Failed" : "Successful");

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Verify - cache should be entirely empty
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            System.out.print("Verifying: checking cache is now entirely empty ... ");

            finalObjectCount = getObjectCountInSet(client, TEST_NAMESPACE_NAME, TEST_SET_NAME);
            if (finalObjectCount > 0) {
                throw new Exception("Cache not empty, it has " + finalObjectCount + " objects");
            }
            System.out.println(failed ? "Failed" : "Successful");

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (client != null) {
            client.close();
        }
    }


    private static long getObjectCountInSet(AerospikeClient client, String namespaceName, String setName) {
        // Counting records in a set using Info
        long objectCount = 0;
        Node[] nodes = client.getNodes();
        for (Node node : nodes) {
            // Invoke an info call to each node in the cluster and sum the objectCount value
            // The infoString will contain a result like this:
            // objects=0:tombstones=0:memory_data_bytes=0:device_data_bytes=0:truncate_lut=0:stop-writes-count=0:disable-eviction=false;
            String infoString = Info.request(node, "sets/" + namespaceName + "/" + setName);
            if (infoString.equals("") || infoString.equals("ns_type=unknown")) continue;

            String objectsString = infoString.substring(infoString.indexOf("=") + 1, infoString.indexOf(":"));
            objectCount += Long.parseLong(objectsString);
        }

        return objectCount;
    }


    static class BenchmarkWorker implements Runnable {
        private CacheItemUsageTracking cachedItemTracker;
        private int operationsPerThreadCount;
        private int lruTTL_sec;
        private Random random;

        public BenchmarkWorker(CacheItemUsageTracking cachedItemTracker, int operationsPerThreadCount, int ttl_sec, Random random) {
            this.cachedItemTracker = cachedItemTracker;
            this.operationsPerThreadCount = operationsPerThreadCount;
            this.lruTTL_sec = ttl_sec;
            this.random = random;
        }

        public void run() {

            Key testKey = cachedItemTracker.getKey();

            AerospikeClient client = null;

            try {

                // Connect to the clusterbbbbbbb
                client = createAerospikeClient();
                WritePolicy writePolicy = new WritePolicy();
                writePolicy.expiration = AEROSPIKE_CONF_LRU_TTL;

                int n = this.operationsPerThreadCount;
                while (n-- > 0) {

                    ////////////////////////////////////////////////////////////////////////////////////////////////////
                    // User's code - to keep a cached item alive

                    // Reset the cached items TTL and fetch the record - this is done atomically on the Aerospike
                    // server where record exists (single network call)
                    Operation[] fetchCachedItemOperation = new Operation[]{
                            Operation.touch(),
                            Operation.get()
                    };

                    Record r = client.operate(writePolicy, testKey, fetchCachedItemOperation);
                    if (r == null) {
                        throw new Exception("Record should still exist: " + this.cachedItemTracker.getRecordId());
                    }

                    System.out.println("DEBUG: Kept record alive key=" + testKey + ", TTL=" + r.getTimeToLive());

                    ////////////////////////////////////////////////////////////////////////////////////////////////////

                    // This is for testing only
                    this.cachedItemTracker.hit();

                    // Show use being busy
                    if (n % 20 == 0) System.out.println(".");
                    //System.out.print(".");

                    // Keep the cache item alive, use time (seconds) between 0 and (TTL / 2) - just before expiry
//                    int randomTime_sec = random.nextInt(lruTTL_sec );
                    Thread.sleep( 1000);

                }
            } catch (Exception e) {
                System.out.println("ERROR: Problem record with digest=" + ByteToHex.convert(testKey.digest) + ": " + e.getMessage());
                e.printStackTrace();
            } finally {

                if (client != null) {
                    client.close();
                }
            }
        }
    }
}
