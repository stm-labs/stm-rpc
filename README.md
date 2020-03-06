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

**Add snapshot dependency**

```xml
.....
<dependency>
    <groupId>ru.stm-labs.rpc</groupId>
    <artifactId>rpc-kakfa-redis</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
.....
<repositories>
    <repository>
        <id>maven-snapshots</id>
        <url>http://oss.sonatype.org/content/repositories/snapshots</url>
        <layout>default</layout>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
.....    
```



----
STM Labs 2020 &copy;