FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/mmmanyfold.jar /mmmanyfold/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/mmmanyfold/app.jar"]
