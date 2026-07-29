# ===== 1단계: 프론트엔드 빌드 =====
FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ===== 2단계: 백엔드 빌드 (프론트 결과물 포함) =====
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
# 프론트 빌드 결과를 Spring Boot 정적 리소스로 복사
COPY --from=frontend /app/frontend/dist/ ./src/main/resources/static/
RUN mvn -B clean package -DskipTests

# ===== 3단계: 실행 =====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
# 컨테이너 메모리의 70%를 힙으로 (네이티브 메모리 여유 확보)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+UseG1GC"
# Cloud Run이 주입하는 PORT를 사용 (기본 8080)
CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]