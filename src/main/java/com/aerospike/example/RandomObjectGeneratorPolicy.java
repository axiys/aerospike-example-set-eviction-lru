package com.aerospike.example;

import com.aerospike.client.*;
import com.aerospike.client.policy.WritePolicy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

class RandomObjectGeneratorPolicy implements Runnable {
    AerospikeClient client;
    private AtomicBoolean cancelled;
    private String namespace;
    private String setName;
    private String binName;
    private int configTTL;
    private int maxObjectsToCreateRandomly;
    private static Random random = new Random(LocalDateTime.now().getNano() * LocalDateTime.now().getSecond());

    public RandomObjectGeneratorPolicy(AtomicBoolean cancelled, AerospikeClient client, String namespace, String setName, String binName, int configTTL, int maxObjectsToCreateRandomly) {
        this.client = client;
        this.cancelled = cancelled;
        this.namespace = namespace;
        this.setName = setName;
        this.binName = binName;
        this.configTTL = configTTL;
        this.maxObjectsToCreateRandomly = maxObjectsToCreateRandomly;
    }

    public void run() {

        try {
            // Periodically show number objects in the various histogram TTL buckets
            while (!cancelled.get()) {
                RandomObjectGenerator.generate(client, namespace, setName, binName, configTTL, random.nextInt(maxObjectsToCreateRandomly));

                Thread.sleep(1000);
            }

        } catch (Exception e) {
            // TODO:auto recovery after error
            e.printStackTrace();

        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // RandomObjectGenerator
    //
    //   GIVEN  a specific TTL
    //   GIVEN  the number of objects to create
    //   THEN   generate random objects in TTL buckets
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static class RandomObjectGenerator {
        private static Random random = new Random(LocalDateTime.now().getNano() * LocalDateTime.now().getSecond());

        // Use factory methods
        private RandomObjectGenerator() {
        }


        private static String generateRandomString(Random random) {
            byte[] array = new byte[16];
            random.nextBytes(array);
            return new String(array, StandardCharsets.UTF_8);
        }

        public static List<String> generate(AerospikeClient client, String namespace, String set, String bin, int lruTTL_sec, int numberOfObjects) {
            WritePolicy writePolicy = new WritePolicy();
            writePolicy.expiration = lruTTL_sec;
            String recordIdPrefix = "record_id-" + UUID.randomUUID() + "-";
            List<String> recordIds = new ArrayList<>();

            for (int i = 0; i < numberOfObjects; i++) {
                String recordId = recordIdPrefix + i;
                Key key = new Key(namespace, set, recordId);
                client.put(writePolicy, key, new Bin(bin, generateRandomString(random)));
                recordIds.add(recordId);


                // Launch at random times
                int randomTime_sec = random.nextInt(lruTTL_sec);
                try {
                    Thread.sleep(randomTime_sec);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            return recordIds;
        }
    }
}


