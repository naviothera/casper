################################################################################
# BASE IMAGE WITH DEPENDENCIES
################################################################################

FROM gradle:6.2.2-jdk11 as dependencies

ENV APP_HOME=/app
WORKDIR ${APP_HOME}

COPY build.gradle.kts settings.gradle.kts gradle.properties ./

RUN gradle resolveDependencies

################################################################################
# IMAGE WITH SOURCES
################################################################################

FROM dependencies as sources

COPY . .