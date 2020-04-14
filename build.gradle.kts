import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.navio.apollo"
val version: String by project
val snapshot: String by project
val branchPublicationName: String by project

plugins {
    id("idea")
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
    id("org.jlleitschuh.gradle.ktlint") version "9.0.0"
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("plugin.noarg")
    kotlin("plugin.allopen")
    kotlin("kapt")
    id("org.flywaydb.flyway") version "6.0.4"
}

java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
    jcenter()
    mavenCentral()
    mavenLocal()
}

// For caching in CI/CD pipelines, from here: https://gist.github.com/matthiasbalke/3c9ecccbea1d460ee4c3fbc5843ede4a
tasks.register("resolveDependencies") {
    group = "build setup"
    description = "Resolve and prefetch dependencies"

    doLast {
        fun resolve(configurations: ConfigurationContainer) {
            configurations
                .filter { c: Configuration -> c.isCanBeResolved }
                .forEach { c -> c.resolve() }
        }
        project.rootProject.allprojects.forEach { subProject ->
            resolve(subProject.buildscript.configurations)
            resolve(subProject.configurations)
        }
    }
}

allOpen {
    annotation("javax.persistence.Entity")
    annotation("javax.persistence.MappedSuperclass")
    annotation("javax.persistence.Embeddable")
}

kapt {
    correctErrorTypes = true
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.slf4j:slf4j-api:1.7.26")
    implementation("javax.inject:javax.inject:1")
    implementation("org.postgresql:postgresql:42.2.2")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("com.graphql-java-kickstart:graphql-spring-boot-starter:5.10.0")
    implementation("com.graphql-java-kickstart:graphiql-spring-boot-starter:5.10.0")
    implementation("com.graphql-java-kickstart:graphql-spring-boot-starter-test:5.10.0")

    implementation("org.flywaydb:flyway-core:6.1.3")

    implementation("org.hibernate:hibernate-jpamodelgen:5.3.7.Final")
    kapt("org.hibernate:hibernate-jpamodelgen:5.3.7.Final")
    implementation("com.vladmihalcea:hibernate-types-52:2.6.1")
    implementation("net.logstash.logback:logstash-logback-encoder:6.2")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.10.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.1")

    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.1")
}

sourceSets {
    create("casper") {
        runtimeClasspath += output
    }

    test {
        compileClasspath += sourceSets["casper"].output
        runtimeClasspath += output + compileClasspath
    }
}

val casperImplementation: Configuration by configurations.getting {}

dependencies {
    casperImplementation(kotlin("reflect"))
    casperImplementation(kotlin("stdlib"))
    casperImplementation(kotlin("stdlib-jdk8"))
    casperImplementation("org.slf4j:slf4j-api:1.7.26")
    casperImplementation("javax.inject:javax.inject:1")
    casperImplementation("org.postgresql:postgresql:42.2.2")
    casperImplementation("org.springframework.boot:spring-boot-starter-web")
    casperImplementation("org.springframework.boot:spring-boot-starter-json")
    casperImplementation("org.springframework.boot:spring-boot-starter-test")
    casperImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    casperImplementation("com.graphql-java-kickstart:graphql-spring-boot-starter:5.10.0")
    casperImplementation("com.graphql-java-kickstart:graphiql-spring-boot-starter:5.10.0")
    casperImplementation("com.graphql-java-kickstart:graphql-spring-boot-starter-test:5.10.0")
    casperImplementation("org.flywaydb:flyway-core:6.1.3")
}

tasks.test {
    jvmArgs = listOf("-Duser.timezone=UTC")
}

fun getArtifactVersion(baseVersion: String, branchPublicationName: String, snapshot: String): String {
    return if (branchPublicationName.trim().isEmpty())
        getArtifactVersion(baseVersion, snapshot)
    else
    // Branch publications are always snapshots
        getArtifactVersion("$baseVersion.${branchPublicationName.trim()}", "true")
}

fun getArtifactVersion(baseVersion: String, snapshot: String): String {
    return when (snapshot.toLowerCase()) {
        "true" -> "$baseVersion-SNAPSHOT"
        else -> baseVersion
    }
}

val artifactMap = mapOf(
    "apollo-framework-casper" to mapOf(
        "version" to getArtifactVersion(version, branchPublicationName, snapshot),
        "groupId" to "${project.group}",
        "taskName" to "buildMain",
        "taskDescription" to "Build jar from core graphql schema files",
        "sourceSet" to "casper",
        "publicationName" to "Core"
    )
)

val publicationsMap = mutableMapOf<String, Map<String, Any>>()

artifactMap.map { (artifactName, map) ->
    // build jar
    val jar = tasks.create<Jar>(map.getValue("taskName")) {
        archiveBaseName.set(artifactName)
        archiveVersion.set(map.getValue("version"))
        description = map.getValue("taskDescription").toString()
        val sourceSet = map.get("sourceSet") ?: "main"
        from(sourceSets[sourceSet].output) {
            include("**")
        }
    }
    // prepare publications map
    publicationsMap.put(
        map.getValue("publicationName"),
        mapOf(
            "artifactId" to artifactName,
            "artifact" to jar,
            "artifactVersion" to map.getValue("version"),
            "groupId" to map.getValue("groupId")
        )
    )
}

publishing {
    publications {
        repositories {
            mavenLocal()
            maven(System.getenv("MAVEN_REPO_URL") ?: "") {
                credentials {
                    username = System.getenv("MAVEN_REPO_USERNAME") ?: "myMavenRepo"
                    password = System.getenv("MAVEN_REPO_PASSWORD") ?: ""
                }
            }
        }
        publicationsMap.forEach { (name, publication) ->
            create(name, MavenPublication::class) {
                groupId = publication.getValue("groupId").toString()
                version = publication.getValue("artifactVersion").toString()
                artifactId = publication.getValue("artifactId").toString()
                artifact(publication.getValue("artifact"))
            }
        }
    }
}
