# repository to reproduce issue with fast-jar && configprovider

Steps to reproduce:

1. ./mvnw package
2. docker build -f src/main/docker/Dockerfile.jvm -t quarkus/code-with-quarkus-jvm .
3. docker run -i --rm -p 8080:8080 quarkus/code-with-quarkus-jvm
4. Hit http://localhost:8080/hello-resteasy