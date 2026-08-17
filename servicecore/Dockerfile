FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw \
	&& ./mvnw \
		--batch-mode \
		--no-transfer-progress \
		-DskipTests \
		dependency:go-offline

COPY src src

RUN ./mvnw \
		--batch-mode \
		--no-transfer-progress \
		-DskipTests \
		package


FROM gcr.io/distroless/java21-debian13:nonroot

WORKDIR /app

COPY --from=build \
	/workspace/target/servicecore-*.jar \
	/app/servicecore.jar

EXPOSE 8080

CMD ["/app/servicecore.jar"]