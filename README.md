# Aerospike Example: Least Recently Used (LRU) Cache
When you have a loose collection of items, we want to remove items that have not been used.

## Introduction
This example demonstrates how am Aerospike namespace can be configured to act like a cache. This code demonstrates how 
you can have an in memory cache were items will only exist for 5 seconds. There will be 32 threads running concurrently
pulling different items off the cache.

### Configure which namespace should hold your LRU
```
namespace lru_test {
    default-ttl 5
    nsup-period 1

	replication-factor 1
	memory-size 1G
}

```
Some notes on the configuration:
* This is an in memory configuration without replication.
* The default-ttl is how long in seconds an item should keep alive, this should be much higher for your workloads.
* The nsup-period is how frequent will the Aerospike eviction process will run.

Enhancements (see Aerospike documentation):
* You can do fine-grained control of TTLs at the record level - you can have different caches per set per record (using WritePolicy)
* You can set up a distributed cache by adding more nodes and increasing the replication-factor to 2

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
* There can be cases where an Aerospike cluster node or parition becomes unavailable, always check the 
  AerospikeException reason codes and apply appropriate retry policies or compensation steps
  
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
$docker kill $(docker ps -q) -f; docker rm $(docker ps -a -q) -f
$docker kill $(docker ps -q) -f; docker rm $(docker ps -a -q) -f ; docker rmi $(docker images -q -a) -f
```
