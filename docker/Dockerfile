FROM bellsoft/liberica-openjdk-alpine:21 AS jvm_build

WORKDIR /app


COPY ./*gradle* /app/
COPY ./gradle /app/gradle
RUN ./gradlew -i --write-verification-metadata sha256 help

COPY . /app
RUN rm ./gradle/verification-metadata.xml && ./gradlew -i installDist

FROM bellsoft/liberica-openjre-alpine:21

ENV APPLICATION_USER ktor
RUN adduser -D -g '' $APPLICATION_USER

RUN mkdir /app
RUN chown -R $APPLICATION_USER /app

USER $APPLICATION_USER

COPY --from=jvm_build /app/build/install/mattermost-chatgpt-bot /app
WORKDIR /app

ENTRYPOINT ["/app/bin/mattermost-chatgpt-bot"]