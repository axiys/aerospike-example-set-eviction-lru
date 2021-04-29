package com.aerospike.example;

import com.aerospike.client.*;
import com.aerospike.client.cluster.Node;
import com.aerospike.client.policy.Priority;
import com.aerospike.client.policy.ScanPolicy;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class ManageMaxObjectsInLRUCachePolicy implements Runnable {
    AerospikeClient client;
    private AtomicBoolean cancelled;
    private String namespace;
    private String setName;
    private int configTTL;
    private long goalTotalMaxObjects;
    private int checkFrequency;

    public ManageMaxObjectsInLRUCachePolicy(AtomicBoolean cancelled, AerospikeClient client, String namespace, String setName, int configTTL, long goalTotalMaxObjects, int checkFrequency) {
        this.client = client;
        this.cancelled = cancelled;
        this.namespace = namespace;
        this.setName = setName;
        this.configTTL = configTTL;
        this.goalTotalMaxObjects = goalTotalMaxObjects;
        this.checkFrequency = checkFrequency;
    }

    public void run() {

        try {
            // Periodically show number objects in the various histogram TTL buckets
            while (!cancelled.get()) {

                // State Space: get the state of the namespace and set
                ObjectsPerTTLHistogramState state = ObjectsPerTTLHistogramState.fetch(client, namespace, setName, configTTL);

                System.out.println("\n" + state);

                // Calculate the eviction behavior's using a reward function
                if (!ManageMaxObjectsInLRUCacheRewardFunction.is_satisfied(state, goalTotalMaxObjects)) {
                    // Not satisfied, so run our policy again with the following parameters
                    long subGoalObjectsToRemove = state.totalObjects - goalTotalMaxObjects;

                    // Find the TTL bucket with some objects to remove, start from oldest
                    int candidateBucketIndex = -1;
                    long candidateBucketCount = -1;
                    for (int bucketIndex = 0; bucketIndex < state.objectsPerBucket.length; bucketIndex++) {
                        long objectCount = state.objectsPerBucket[bucketIndex];
                        if (objectCount > 0) {
                            candidateBucketIndex = bucketIndex;
                            candidateBucketCount = objectCount;
                            break;
                        }
                    }
                    if (candidateBucketIndex == -1) {
                        System.out.println("WARNING: No candidate buckets found to remove from");
                    } else {

                        // What's the TTL low watermark we need to look above from
                        // - TTL bucket sizes can't be fractional, they need to be 1s min. Consider when our configTTL
                        //   is less than the number of buckets
                        long candidateBucketTTL = state.calculateBucketTTL(candidateBucketIndex);

                        // How many to remove this bucket?
                        // - Consider that we may not have enough, we will get more on next iteration
                        long candidateBucketRemoveCount = Math.min(subGoalObjectsToRemove, candidateBucketCount);
                        System.out.println("LRU POLICY - State:\n  Total Objects:\t\t\t\t" + state.totalObjects + "\n  Goal Max Objects:\t\t\t\t" + goalTotalMaxObjects + "\n> Objects To Remove:\t\t\t" + subGoalObjectsToRemove + "\n\n> Candidate Bucket Index:\t\t" + candidateBucketIndex + "\n  Candidate Bucket TTL:\t\t\t" + candidateBucketTTL + "\n> Candidate Bucket Remove:\t\t" + candidateBucketRemoveCount + "/" + candidateBucketCount + "\n");

                        ForceObjectEvictionPolicy.run(client, namespace, setName, candidateBucketTTL, candidateBucketRemoveCount);
                    }
                }

                // Run frequently
                Thread.sleep(checkFrequency);
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
    // ObjectsPerTTLHistogram
    //   GIVEN  an Aerospike cluster with multiple nodes
    //   GIVEN  namespace with TTL evictions enabled
    //   WHEN   fetching objects per TTL histogram
    //   THEN   calculate the total number of objects in each TTL bucket across all nodes
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static class ForceObjectEvictionPolicy {
        // Use factory methods
        private ForceObjectEvictionPolicy() {
        }

        private static void run(AerospikeClient client, String namespace, String setName, long ttlLowWatermark, long subgoal_objects_to_remove) {
            ScanPolicy policy = new ScanPolicy();
            policy.concurrentNodes = true;
            policy.priority = Priority.HIGH;
            policy.includeBinData = false;
            policy.failOnClusterChange = true;
            policy.scanPercent = 100;

            AtomicLong objects_removed_count = new AtomicLong(0);

            try {
                client.scanAll(policy, namespace, setName, (key, record) -> {

                    int recordTTL = record.getTimeToLive();
                    if (recordTTL <= ttlLowWatermark) {
                        if (client.delete(null, key)) {
                            //System.out.println("DEBUG: Removed record with digest=" + ByteToHex.convert(key.digest) + " - TTL=" + recordTTL);

                            // Count how many we were able to delete. NOTE: scan can miss some
                            if (objects_removed_count.incrementAndGet() >= subgoal_objects_to_remove) {
                                throw new AerospikeException.ScanTerminated();
                            }
                        }
                    }
                });

            } catch (AerospikeException.ScanTerminated ex) {
                // Ignore
            } finally {
                System.out.println(">>Removed " + objects_removed_count + "/" + subgoal_objects_to_remove + " objects");
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ManageMaxObjectsInLRUCacheRewardFunction
    //
    //   GIVEN  a goal of max number objects to keep
    //   GIVEN  the state of the namespace and set
    //   THEN   calculate if the goal has been satisifed
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static class ManageMaxObjectsInLRUCacheRewardFunction {
        private static boolean is_satisfied(ObjectsPerTTLHistogramState state, long goal_total_max_objects) {
            return state.totalObjects < goal_total_max_objects;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ObjectsPerTTLHistogram
    //
    //   GIVEN  an Aerospike cluster with multiple nodes
    //   GIVEN  namespace with TTL evictions enabled
    //   WHEN   fetching objects per TTL histogram
    //   THEN   calculate the total number of objects in each TTL bucket across all nodes
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static class ObjectsPerTTLHistogramState {
        long configTTL;

        private long timePerUnit = 0;
        private long durationPerBucket = 0;
        private long totalBuckets = 0;
        private long totalObjects = 0;

        // Ordered by oldest first
        private long[] objectsPerBucket = null;

        // Use factory methods
        private ObjectsPerTTLHistogramState(long configTTL) {
            this.configTTL = configTTL;
        }

        public String toString() {

            StringBuilder bucketMapHeader = new StringBuilder();
            StringBuilder bucketMapTTL = new StringBuilder();
            StringBuilder bucketMapCount = new StringBuilder();
            for (int bucketIndex = 0; bucketIndex < totalBuckets; bucketIndex++) {

                // Minimum TTL is 1 second, so ignore irrelevant bucket
                long bucketTTL = calculateBucketTTL(bucketIndex);
                if (bucketTTL > configTTL) break;

                if (bucketMapTTL.length() > 0) {
                    bucketMapTTL.append('|');
                }
                bucketMapTTL.append(String.format("%3d", bucketTTL));

                if (bucketMapHeader.length() > 0) {
                    bucketMapHeader.append('|');
                }
                bucketMapHeader.append(String.format("%3s", bucketIndex));

                if (bucketMapCount.length() > 0) {
                    bucketMapCount.append('|');
                }
                bucketMapCount.append(String.format("%3d", objectsPerBucket[bucketIndex]));
            }

            String line = "------------------------------------------------------------------------------------------------------------------------";
            String summary = String.format("totalObjects=%d, timePerUnit=%d, durationPerBucket=%d", totalObjects, timePerUnit, durationPerBucket);
            return line + "\nTTL Buckets Histogram:\nIdx: " + bucketMapHeader + "\nTTL: " + bucketMapTTL + "\n # : " + bucketMapCount + "\n" + line + "\n" + summary + "\n";
        }

        public long calculateBucketTTL(int index) {
            long bucketTTL = index * durationPerBucket;
            return bucketTTL;
        }

        public static ObjectsPerTTLHistogramState fetch(AerospikeClient client, String namespace, String set, long configTTL) {
            // Reset cache
            ObjectsPerTTLHistogramState state = new ObjectsPerTTLHistogramState(configTTL);

            // Get total object counts in each TTL bucket
            Node[] nodes = client.getNodes();
            for (Node node : nodes) {
                // Invoke an info call to each node in the cluster and total up the object count value for each TTL bucket
                String request = "histogram:namespace=" + namespace + ";set=" + set + ";type=ttl";
                try {
                    String infoString = Info.request(node, request);
                    // Example: histogram:namespace=lru_test;set=mycache;type=ttl > units=seconds:hist-width=100:bucket-width=1:buckets=0,0,0,0,0,0,0,0,0,0,0,0,973,4,2,5,2,2,4,8,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
                    System.out.println("\nDEBUG: " + request + " > " + infoString);

                    // Cache units
                    if (state.timePerUnit == 0) {
                        String units = infoString.substring(infoString.indexOf("units=") + 6, infoString.indexOf(":", infoString.indexOf("units=") + 6));
                        if (!units.equals("seconds")) {
                            throw new InvalidParameterException("Unexpected units: " + units);
                        }

                        // Always seconds
                        state.timePerUnit = 1;
                    }

                    // Cache bucket-width
                    if (state.durationPerBucket == 0) {
                        state.durationPerBucket = Long.parseLong(infoString.substring(infoString.indexOf("bucket-width=") + 13, infoString.indexOf(":", infoString.indexOf("bucket-width=") + 13)));
                    }

                    // Calculate total objects for each TTL buckets across all nodes
                    long[] buckets = Arrays.stream(infoString.substring(infoString.indexOf("buckets=") + 8).split(",")).map(Long::parseLong).mapToLong(l -> l).toArray();
                    if (state.objectsPerBucket == null) {
                        state.objectsPerBucket = buckets;
                    } else {
                        // Total up number of objects per TTL
                        IntStream.range(0, buckets.length).forEach(i ->
                                state.objectsPerBucket[i] += buckets[i]
                        );
                    }

                } catch (Exception ex) {
                    System.out.println("\n" + request + " > ERROR: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }

            // Post calculations
            state.totalObjects = Arrays.stream(state.objectsPerBucket).reduce(0, Long::sum);
            state.totalBuckets = state.objectsPerBucket.length;

            return state;
        }
    }
}
