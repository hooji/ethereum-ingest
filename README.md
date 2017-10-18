# ethereum-ingest [![Build Status](https://travis-ci.org/codingchili/ethereum-ingest.svg?branch=master)](https://travis-ci.org/codingchili/ethereum-ingest)
Ingests events from the Ethereum blockchain into ElasticSearch, MongoDB, Hazelcast, CQEngine and SQLite.

In development; tested with ElasticSeach 5.6.2 and geth 1.7.1.

Build with
```
gradle jar
```
Requires chili-core through jitpack or local repo.

Start geth with rpc enabled
```

geth --rpcapi personal,db,eth,net,web3 --rpc --testnet
```

Run with
```
java -jar <filename>.jar
```

Set configuration in application.json.

Default configuration
```
{
    "storage": "ELASTICSEARCH",
    "ipc": "\\\\.\\pipe\\geth1.ipc",
    "os": "WINDOWS",
    "blockIndex": "ether-block-demo",
    "txIndex": "ether-tx-demo",
    "txImport": true,
    "startBlock": "1890000",
    "backpressure": 9223372036854775807
}
```


Storage can be any of the following
- MONGODB
- ELASTICSEARCH
- HAZELCAST
- SQLITE
- MEMORY

os can be any of the following
- UNIX
- WINDOWS
