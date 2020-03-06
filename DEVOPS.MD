**Build**
 
 ```shell script
mvn install
```
 
**Deploy Maven Central Deploy**
```shell script
./deploy.sh
```

**Update version**
```shell script
mvn versions:set -DgenerateBackupPoms=false -DprocessAllModules=true  -DnewVersion=1.0.0.RELEASE
```
