package com.aerospike.example;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Info;
import com.aerospike.client.cluster.Node;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class ManageMaxObjectsInLRUCacheWorker implements Runnable {
    AerospikeClient client;
    private AtomicBoolean cancelled;
    private String namespace;
    private String set;

    public ManageMaxObjectsInLRUCacheWorker(AtomicBoolean cancelled, AerospikeClient client, String namespace, String set) {
        this.client = client;
        this.cancelled = cancelled;
        this.namespace = namespace;
        this.set = set;
    }

//    long[] getObjectsPerBucket() {
//        return objectCountsPerTTLBucket;
//    }

    public void run() {

        try {
            // Periodically show number objects in the various histogram TTL buckets
            while (!cancelled.get()) {
                Thread.sleep(1000);
                ObjectsPerTTLHistogram histogram = ObjectsPerTTLHistogram.fetch(client, namespace, set);
                System.out.println(histogram);
            }

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ObjectsPerTTLHistogram
    //   GIVEN  an Aerospike cluster with multiple nodes
    //   GIVEN  namespace with TTL evictions enabled
    //   WHEN   fetching objects per TTL histogram
    //   THEN   calculate the total number of objects in each TTL bucket across all nodes
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static class ObjectsPerTTLHistogram {
        public long timePerUnit = 0;
        public long durationPerBucket = 0;
        public long[] objectsPerBucket = null;

        private ObjectsPerTTLHistogram() {
        }

        public String toString() {
            return String.format("timePerUnit=%d, durationPerBucket=%d, objectsPerBucket=%s", timePerUnit, durationPerBucket, Arrays.stream(objectsPerBucket).mapToObj(Long::toString).collect(Collectors.joining(",")));
        }

        public static ObjectsPerTTLHistogram fetch(AerospikeClient client, String namespace, String set) {
            // Reset cache
            ObjectsPerTTLHistogram histogram = new ObjectsPerTTLHistogram();

            // Get total object counts in each TTL bucket
            Node[] nodes = client.getNodes();
            for (Node node : nodes) {
                // Invoke an info call to each node in the cluster and total up the object count value for each TTL bucket
                String request = "histogram:namespace=" + namespace + ";set=" + set + ";type=ttl";
                try {
                    String infoString = Info.request(node, request);
                    // Example: histogram:namespace=lru_test;set=mycache;type=ttl > units=seconds:hist-width=100:bucket-width=1:buckets=0,0,0,0,0,0,0,0,0,0,0,0,973,4,2,5,2,2,4,8,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
                    //System.out.println("\n" + request + " > " + infoString);

                    // Cache units
                    if (histogram.timePerUnit == 0) {
                        String units = infoString.substring(infoString.indexOf("units=") + 6, infoString.indexOf(":", infoString.indexOf("units=") + 6));
                        if (!units.equals("seconds")) {
                            throw new InvalidParameterException("Unexpected units: " + units);
                        }

                        // Always seconds
                        histogram.timePerUnit = 1;
                    }

                    // Cache bucket-width
                    if (histogram.durationPerBucket == 0) {
                        histogram.durationPerBucket = Long.parseLong(infoString.substring(infoString.indexOf("bucket-width=") + 13, infoString.indexOf(":", infoString.indexOf("bucket-width=") + 13)));
                    }

                    // Calculate total objects for each TTL buckets across all nodes
                    long[] buckets = Arrays.stream(infoString.substring(infoString.indexOf("buckets=") + 8).split(",")).map(Long::parseLong).mapToLong(l -> l).toArray();
                    if (histogram.objectsPerBucket == null) {
                        histogram.objectsPerBucket = buckets;
                    } else {
                        // Total up number of objects per TTL
                        IntStream.range(0, buckets.length).forEach(i ->
                                histogram.objectsPerBucket[i] += buckets[i]
                        );
                    }

                } catch (Exception ex) {
                    System.out.println("\n" + request + " > ERROR: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }

            return histogram;
        }
    }
}
