# Aerospike Example: Least Recently Used (LRU) Cache - TTL and Max Objects
When you have a loose collection of items, we want to remove items that have not been used for a period of time (called TTL - Time To Live). 

Also, only have a maximum number of objects in the LRU cache.

## Introduction
This example demonstrates how am Aerospike namespace can be configured to act like a cache. This code demonstrates how 
you can have an in memory cache were items will only exist for 5 seconds. There will be 32 threads running concurrently
pulling different items off the cache.

It also, shows how TTL histograms can be used to identify the oldest items to remove when the number of objects exceed a 
controlled size so that the memory is not breached.

### Configure which namespace should hold your LRU
```
namespace lru_test {
    default-ttl 10
    nsup-period 1
    nsup-hist-period 1
    evict-hist-buckets 200000

    replication-factor 1
    memory-size 1G
}

```

Some notes on the configuration:
* This is an in memory configuration without replication.
* The default-ttl is how long in seconds an item should keep alive, this should be much higher for your workloads (defalut disabled): https://www.aerospike.com/docs/reference/configuration/#default-ttl
* The nsup-period is how frequent will the Aerospike eviction process will run (default disabled): https://www.aerospike.com/docs/reference/configuration/#nsup-period
* The nsup-hist-period is how frequest will the histograms will be update (default 3600): https://www.aerospike.com/docs/reference/configuration/#nsup-hist-period
* The evict-hist-buckets controls the number of eviction histogram buckets - provides the a level of granuality 

https://discuss.aerospike.com/t/records-ttl-and-evictions-for-aerospike-server-version-prior-to-3-8/737

Enhancements (see Aerospike documentation):
* You can do fine-grained control of TTLs at the record level - you can have different caches per set per record (using WritePolicy).
* You can set up a distributed cache by adding more nodes and increasing the replication-factor to 2.
* In production records of particular TTL are placed in buckets (default is 10,000) so configure upto 10,000,000 for better fine grain control: see https://discuss.aerospike.com/t/eviction-mechanisms-in-aerospike/2854

Further information is available at https://www.aerospike.com/docs/operations/configure/namespace/retention/index.html

### Client
To fetch item you would perform the following atomic operation to keep a cached item alive in the cache:
```
// Reset the cached items TTL and fetch the record - this is done atomically on the Aerospike
// server where record exists (single network call)
Operation[] fetchCachedItemOperation = new Operation[]{
        Operation.touch(),
        Operation.get()
};

Record r = client.operate(null, testKey, fetchCachedItemOperation);
if (r == null) {
    throw new Exception("Record should still exist: " + this.cachedItemTracker.getId());
}
```
Enhancements (see Aerospike documentation):
* There can be cases where an Aerospike cluster node or partition becomes unavailable, always check the 
  AerospikeException reason codes and apply appropriate retry policies or compensation steps

The ManageMaxObjectInLRUCachePolicy is an example of how a TTL histogram is used to identify which TTL bucket to remove from if the number of objects in the LRU cache exceeds a certain goal.

```
// Example usage
new Thread(new ManageMaxObjectsInLRUCachePolicy(cancelMonitor, createAerospikeClient(), TEST_NAMESPACE_NAME, TEST_SET_NAME, AEROSPIKE_CONF_LRU_TTL, TEST_GOAL_MAX_DATA_SET_SIZE)).start();
```
## Dependencies
* Maven
* Java 8
* Aerospike Client 4.4.6
* Aerospike Server CE 4.8.0.6
* Docker Compose

## Usage
Start Aerospike service using docker (expose port 4000 locally so that it doesn't interfere with default 3000)
```
docker network create backend
docker-compose up -d
```
Build benchmarkdocker network create backend
docker-compose up -d
```
mvn install
mvn package
```
Default run (32 threads, 100 operations per thread):
```
mvn test -Ptest
```
Default run (1 threads, 100 operations per thread):
```
mvn test -Ptest -Dexec.args="-z 1 -c 100"
```
NOTE: To get then number of CPUs on Unbuntu use:
```
lscpu | grep "^CPU(s):"
```
Max hyper threads is number of CPUs x 2

## Troubleshooting
DESTRUCTIVE: WARNING! Removes all docker images and instances
You would use this if you want to experiment with different aerospike.conf and versions of Aerospike servers:
```
$docker kill $(docker ps -q) -f; docker rm $(docker ps -a -q) -f ; docker rmi $(docker images -q -a) -f
```

## Run Example
TTL is 10 seconds
Max Objects 800

```
Creating random records to test in LRU cache ... 
>>Created 1000 new LRU cache objects

Running tests: threads=32, operations per thread=10, ttl=10 seconds ... 
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0|  0|  0|  0|  0|  0|149|194|196|198|132
------------------------------------------------------------------------------------------------------------------------
totalObjects=869, timePerUnit=1, durationPerBucket=1

LRU POLICY - State:
  Total Objects:				869
  Goal Max Objects:				800
> Objects To Remove:			69

> Candidate Bucket Index:		6
  Candidate Bucket TTL:			6
> Candidate Bucket Remove:		69/149

>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Removed 69/69 objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 1 new LRU cache objects
>>Created 56 new LRU cache objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0|  0|  0|  0|  0|127|147|196|198|193|158
------------------------------------------------------------------------------------------------------------------------
totalObjects=1019, timePerUnit=1, durationPerBucket=1

LRU POLICY - State:
  Total Objects:				1019
  Goal Max Objects:				800
> Objects To Remove:			219

> Candidate Bucket Index:		5
  Candidate Bucket TTL:			5
> Candidate Bucket Remove:		127/127

>>Removed 127/127 objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0|  0|  0|  0|  0|127|147|196|198|193|158
------------------------------------------------------------------------------------------------------------------------
totalObjects=1019, timePerUnit=1, durationPerBucket=1

LRU POLICY - State:
  Total Objects:				1019
  Goal Max Objects:				800
> Objects To Remove:			219

> Candidate Bucket Index:		5
  Candidate Bucket TTL:			5
> Candidate Bucket Remove:		127/127

>>Removed 127/127 objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0|  0|  0|  0|  0| 20|196|198|193|126| 32
------------------------------------------------------------------------------------------------------------------------
totalObjects=765, timePerUnit=1, durationPerBucket=1


------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0|  0|  0|  0|  0| 20|196|198|193|126| 32
------------------------------------------------------------------------------------------------------------------------
totalObjects=765, timePerUnit=1, durationPerBucket=1

>>Created 27 new LRU cache objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0|  0|  0|  0| 20|196|198|193|126|  0| 58
------------------------------------------------------------------------------------------------------------------------
totalObjects=791, timePerUnit=1, durationPerBucket=1


------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0|  0|  0|  0| 20|196|198|193|126|  0| 58
------------------------------------------------------------------------------------------------------------------------
totalObjects=791, timePerUnit=1, durationPerBucket=1


------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0|  0|  0| 20|196|198|193|126|  0| 27| 32
------------------------------------------------------------------------------------------------------------------------
totalObjects=792, timePerUnit=1, durationPerBucket=1


------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0|  0|  0| 20|196|198|193|126|  0| 27| 32
------------------------------------------------------------------------------------------------------------------------
totalObjects=792, timePerUnit=1, durationPerBucket=1


------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0|  0| 20|196|198|193|126|  0| 27|  0| 47
------------------------------------------------------------------------------------------------------------------------
totalObjects=807, timePerUnit=1, durationPerBucket=1

LRU POLICY - State:
  Total Objects:				807
  Goal Max Objects:				800
> Objects To Remove:			7

> Candidate Bucket Index:		2
  Candidate Bucket TTL:			2
> Candidate Bucket Remove:		7/20

>>Removed 7/7 objects
>>Created 100 new LRU cache objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0|  0| 20|196|198|193|126|  0| 27|  0| 47
------------------------------------------------------------------------------------------------------------------------
totalObjects=807, timePerUnit=1, durationPerBucket=1

LRU POLICY - State:
  Total Objects:				807
  Goal Max Objects:				800
> Objects To Remove:			7

> Candidate Bucket Index:		2
  Candidate Bucket TTL:			2
> Candidate Bucket Remove:		7/20

>>Removed 7/7 objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0| 20|182|198|193|126|  0| 27|  0| 43| 89
------------------------------------------------------------------------------------------------------------------------
totalObjects=878, timePerUnit=1, durationPerBucket=1

LRU POLICY - State:
  Total Objects:				878
  Goal Max Objects:				800
> Objects To Remove:			78

> Candidate Bucket Index:		1
  Candidate Bucket TTL:			1
> Candidate Bucket Remove:		20/20

>>Removed 20/20 objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :   0| 20|182|198|193|126|  0| 27|  0| 43| 89
------------------------------------------------------------------------------------------------------------------------
totalObjects=878, timePerUnit=1, durationPerBucket=1

LRU POLICY - State:
  Total Objects:				878
  Goal Max Objects:				800
> Objects To Remove:			78

> Candidate Bucket Index:		1
  Candidate Bucket TTL:			1
> Candidate Bucket Remove:		20/20

>>Removed 20/20 objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :  20|142|198|193|126|  0| 27|  0| 43| 57| 32
------------------------------------------------------------------------------------------------------------------------
totalObjects=838, timePerUnit=1, durationPerBucket=1

LRU POLICY - State:
  Total Objects:				838
  Goal Max Objects:				800
> Objects To Remove:			38

> Candidate Bucket Index:		0
  Candidate Bucket TTL:			0
> Candidate Bucket Remove:		20/20

>>Removed 0/20 objects
>>Created 15 new LRU cache objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # :  20|142|198|193|126|  0| 27|  0| 43| 57| 32
------------------------------------------------------------------------------------------------------------------------
totalObjects=838, timePerUnit=1, durationPerBucket=1

LRU POLICY - State:
  Total Objects:				838
  Goal Max Objects:				800
> Objects To Remove:			38

> Candidate Bucket Index:		0
  Candidate Bucket TTL:			0
> Candidate Bucket Remove:		20/20

>>Removed 0/20 objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # : 142|198|193|126|  0| 27|  0| 43| 57|  0| 47
------------------------------------------------------------------------------------------------------------------------
totalObjects=833, timePerUnit=1, durationPerBucket=1

LRU POLICY - State:
  Total Objects:				833
  Goal Max Objects:				800
> Objects To Remove:			33

> Candidate Bucket Index:		0
  Candidate Bucket TTL:			0
> Candidate Bucket Remove:		33/142

>>Removed 0/33 objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # : 142|198|193|126|  0| 27|  0| 43| 57|  0| 47
------------------------------------------------------------------------------------------------------------------------
totalObjects=833, timePerUnit=1, durationPerBucket=1

LRU POLICY - State:
  Total Objects:				833
  Goal Max Objects:				800
> Objects To Remove:			33

> Candidate Bucket Index:		0
  Candidate Bucket TTL:			0
> Candidate Bucket Remove:		33/142

>>Removed 0/33 objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # : 391|126|  0| 27|  0| 43| 57|  0| 15| 32|  0
------------------------------------------------------------------------------------------------------------------------
totalObjects=691, timePerUnit=1, durationPerBucket=1


------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # : 391|126|  0| 27|  0| 43| 57|  0| 15| 32|  0
------------------------------------------------------------------------------------------------------------------------
totalObjects=691, timePerUnit=1, durationPerBucket=1

>>Created 126 new LRU cache objects

------------------------------------------------------------------------------------------------------------------------
TTL Buckets Histogram:
Idx:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
TTL:   0|  1|  2|  3|  4|  5|  6|  7|  8|  9| 10
 # : 391|126|  0| 27|  0| 43| 57|  0| 15| 32|  0
------------------------------------------------------------------------------------------------------------------------
totalObjects=691, timePerUnit=1, durationPerBucket=1


Verifying: some cache items are still around before their TTL ... Successful
Verifying: expected records have been kept alive... Successful
Verifying: wait for just after TTL, all cache records should have disappeared ... Successful
Verifying: checking cache is now entirely empty ... Successful

```
