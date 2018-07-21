FROM registry.i.hosaka.io/bootstrap:8-jre-alpine
COPY ./target/uberjar/oscillator.jar /srv/oscillator.jar
WORKDIR /srv

EXPOSE 8080 8079

ENTRYPOINT /usr/bin/bootstrap /usr/bin/java -Xms128m -Xmx512m -jar /srv/oscillator.jar