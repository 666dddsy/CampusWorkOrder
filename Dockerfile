#基础镜像：
FROM eclipse-temurin:21-jre-jammy
#容器内工作目录
WORKDIR /app
# 把本机 Maven 打好的 jar 复制进镜像，并改名为 app.jar
COPY target/CampusWorkOrder-0.0.1-SNAPSHOT.jar app.jar
#声明应用在容器内监听 8080
EXPOSE 8080
#容器启动时执行的命令
ENTRYPOINT ["java","-jar","app.jar"]