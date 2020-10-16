mahinadb
============================================

## Introduction

基于akka distributed data 实现的分布式KV DB。

## Implementation

### Conflict-free replicated data type

A conflict-free replicated data type (CRDT) is an abstract data type, with a well-defined interface ,designed to be replicated at multiple processes and exhibiting the following properties:

- any replica can be modified without coordinating with another replicas
- when any two replicas have received the same set of updates, they reach the same state, deterministically, by adopting mathematically sound rules to guarantee state convergence.

In caching, we need at least 3 operations: get, put, and delete. These operations will be defined as case classes for the sake of type-safety.


### Distributed Data

### 

## Usage

1. Lunch seed nodes, there two seed node defined in `application.conf`:

```text
    seed-nodes = [
    "akka://"${clustering.cluster.name}"@"${clustering.seed-ip}":"${clustering.seed-port}
]
```

the `seed` point to localhost `CLUSTER_IP=127.0.0.1` port `CLUSTER_PORT=1600`.

at the first time, construct your cluster gossip keyring. with command:

```shell script
## for seed
sbt "runMain -DCLUSTER_PORT=1600 -DSERVICE_PORT_8080_HTTP_PORT=8600 -DAKKA_MANAGEMENT_ENABLE=enabled"
```

we also note that the environment parameter `AKKA_MANAGEMENT_ENABLE` here use for akka cluster visualization. the visualized project present [here](https://github.com/barudisshu/mahinavis).

![cluster_visualization](/img/cluster_visual_view.png)

2. Join nodes, as for distributed feature that we can scale to support high available. using the follow shell-command to scale a large cluster.

```shell script
sbt "runMain -DCLUSTER_PORT=1601 -DSERVICE_PORT_8080_HTTP_PORT=8601"
sbt "runMain -DCLUSTER_PORT=1602 -DSERVICE_PORT_8080_HTTP_PORT=8602"
```

finally, we have 4 nodes, 

- seed:8600
- join_node0:8601
- join_node1:8602


3. Demo

create data at `seed`

```shell script
curl --location --request PUT '127.0.0.1:8600/replica' \
--header 'Content-Type: application/json' \
--data-raw '{
    "key": "credencial",
    "value": {
        "string": "some_jwt_code"
    }
}'
```

get data from `join_node0`

```shell script
curl --location --request GET '127.0.0.1:8601/replica/token'
```

delete data from `join_node1`

```shell script
curl --location --request DELETE '127.0.0.1:8602/replica/token'
```

get data from `seed`, "not_found" with be result.

terminate `join_node1`, put any data with arbitrary node. and restart `join_node1`. try to get data:

```shell script
curl --location --request GET '127.0.0.1:8602/replica/token'
```

### manual sync

```shell script
curl --location --request POST '127.0.0.1:8600/sync/full' \
--header 'Content-Type: application/json' \
--data-raw '{
    "uri": "http://2ca93e66-0341-4a5b-879a-7d9fba267e3f.mock.pstmn.io"
}'
```

the `uri` is the other data center endpoint.

response,

```json
{
    "meta": {
        "status_code": 200
    },
    "response": "Done. but the sync worker still holding in the background"
}
```

get the sync report,

```shell script
curl --location --request GET '127.0.0.1:8600/sync/last_sync_percentage'
```

response,

```json
{
    "meta": {
        "status_code": 200
    },
    "response": {
        "complete_keys": [],
        "instant": "20-10-12 下午5:50",
        "percentage": 0,
        "sync_number": 0,
        "total_key_number": 0
    }
}
```

### Command line

(not implemented)

## Build image

```shell script
# publish image
sbt docker:publishLocal
# compose
docker-compose up

# check cluster status, to see what happen
docker-compose stop seed
```

## About the seed node

The seed nodes can be started in any order and it is not necessary to have all seed nodes running, but **the node configured as the first element in the seed-nodes configuration list must be started when initially staring a cluster**, otherwise the other seed-nodes will not become initialized and no other node can join the cluster. **Once more than two seed nodes have been started it is no problem to shut down the first seed node**. If the first seed node is restarted, it will first try to join the other seed nodes in the existing cluster.

## Cross DC data sync


## GUI

(not implemented)


## Don’t Build a Distributed Monolith
