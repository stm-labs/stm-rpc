Integration tests from the module "example" includes simple checking of client-server interaction through RPC
These tests uses testcontainers (https://www.testcontainers.org/): during tests kafka and redis docker containers will
be run and stop automatically

By default these tests is skip, to run tests use: -DskipIntegrationTests=false
f.e: mvn verify -DskipIntegrationTests=false