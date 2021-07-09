# STM RPC

![Snapshots build](https://github.com/stm-labs/stm-rpc/actions/workflows/maven.yml/badge.svg?branch=develop)
![Latest maintenance branch](https://github.com/stm-labs/stm-rpc/actions/workflows/maven.yml/badge.svg?branch=release/1.1.x)

Remote Procedure Call framework for Spring Boot based on Kafka / Redis allows organizing two-way communication between services in DMZ and Enterprise Network. When Enterprise Network does not allow any income TCP connections.

**Branches**
- `main` - latest stable release
- `develop` - active development branch (next release)
- `release/**` - maintenance branches

**Builds**
- `develop` branch builds maven artifacts and deploys to sonatype snapshot repository
- `release/**` branches build maven artifacts and deploys to sonatype release repository
- `bugix/**`, `feature/**`, `hotfix/**` branches build maven artifacts on pull request no publishing to maven


**The framework provides out of box**

- Metrics for Prometheus monitoring
- Logging (correlation)
- Annotations based configuration

Please take a look on examples folder

**Projects**:

 - rpc-kafka-redis
 - rpc-local-handler
 - rpc-router

---

**Add dependency to your project** 

```xml
<dependency>
    <groupId>ru.stm-labs.rpc</groupId>
    <artifactId>rpc-kakfa-redis</artifactId>
    <version>1.1.0.RELEASE</version>
</dependency>
```

**Add snapshot dependency**

```xml
.....
<dependency>
    <groupId>ru.stm-labs.rpc</groupId>
    <artifactId>rpc-kakfa-redis</artifactId>
    <version>1.2.0-SNAPSHOT</version>
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
STM Labs 2021 &copy;
