# STM RPC

Remote Procedure Call framework for Spring Boot based on Kafka / Redis allows organizing two-way communication between services in DMZ and Enterprise Network. When Enterprise Network does not allow any income TCP connections.

**The framework provides out of box**

- Metrics for Prometheus monitoring
- Logging (correlation)
- Annotations based configuration

Please take a look on examples folder

**Projects**:

 - rpc-kafka-redis
 - rpc-local-handler
 - rpc-router

 
**Build**
 
 ```shell script
mvn install
```
 
**Deploy Maven Central Deploy**
```shell script
./deploy.sh
```


----
STM Labs 2020 &copy;