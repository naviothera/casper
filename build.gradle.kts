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
    id("org.jlleitschuh.gradle.ktlint") version "10.2.0"
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("plugin.noarg")
    kotlin("plugin.allopen")
    kotlin("kapt")
    id("org.jetbrains.dokka") version "1.6.10"
    id("org.flywaydb.flyway") version "8.0.5"
    signing
}

java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    jcenter()
    mavenCentral()
    mavenLocal()
}

ktlint {
    disabledRules.add("import-ordering")
    filter {
        exclude("build/generated/**")
        include("src/**/kotlin/**/*.kt")
    }
}

tasks.dokkaJavadoc.configure {
    setEnabled(false)
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
        jvmTarget = "11"
    }
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.slf4j:slf4j-api:1.7.26")
    implementation("javax.inject:javax.inject:1")
    implementation("org.postgresql:postgresql:42.3.1")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("com.graphql-java-kickstart:graphql-spring-boot-starter:12.0.0")
    implementation("com.graphql-java-kickstart:graphiql-spring-boot-starter:11.1.0")
    implementation("com.graphql-java-kickstart:graphql-spring-boot-starter-test:12.0.0")
    implementation("com.graphql-java:graphql-java:17.3")

    implementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-test")

    implementation("org.flywaydb:flyway-core:8.0.5")

    implementation("org.hibernate:hibernate-jpamodelgen:5.3.7.Final")
    kapt("org.hibernate:hibernate-jpamodelgen:5.3.7.Final")
    implementation("com.vladmihalcea:hibernate-types-52:2.6.1")
    implementation("net.logstash.logback:logstash-logback-encoder:6.2")
    testImplementation(kotlin("test"))

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.10.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.1")

    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.1")
}

sourceSets {
    create("casper") {
        runtimeClasspath += output
    }

    create("caspergraphql") {
        compileClasspath += sourceSets["casper"].output
        runtimeClasspath += output + compileClasspath
    }

    test {
        compileClasspath += sourceSets["casper"].output + sourceSets["caspergraphql"].output
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
    casperImplementation("org.postgresql:postgresql:42.3.1")
    casperImplementation("org.springframework.boot:spring-boot-starter-web")
    casperImplementation("org.springframework.boot:spring-boot-starter-json")
    casperImplementation("org.springframework.boot:spring-boot-starter-test")
    casperImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    casperImplementation("org.flywaydb:flyway-core:8.0.5")
    casperImplementation("org.apache.commons:commons-lang3:3.12.0")
}

val caspergraphqlImplementation: Configuration by configurations.getting {
    extendsFrom(configurations["casperImplementation"])
}

dependencies {
    caspergraphqlImplementation("com.graphql-java-kickstart:graphql-spring-boot-starter:12.0.0")
    caspergraphqlImplementation("com.graphql-java-kickstart:graphiql-spring-boot-starter:11.1.0")
    caspergraphqlImplementation("com.graphql-java-kickstart:graphql-spring-boot-starter-test:12.0.0")
    caspergraphqlImplementation("com.graphql-java:graphql-java:17.3")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.ktlintCheck)
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
        "libTaskName" to "buildCasperLib",
        "srcTaskName" to "buildCasperSrc",
        "taskDescription" to "Build capser jar from core casper files",
        "sourceSet" to "casper",
        "publicationName" to "Core"
    ),
    "apollo-framework-caspergraphql" to mapOf(
        "version" to getArtifactVersion(version, branchPublicationName, snapshot),
        "groupId" to "${project.group}",
        "libTaskName" to "buildCasperGraphqlLib",
        "srcTaskName" to "buildCasperGraphqlSrc",
        "taskDescription" to "Build caspergraphql jar from casper graphql files",
        "sourceSet" to "caspergraphql",
        "publicationName" to "GraphQL"
    )
)

val publicationsMap = mutableMapOf<String, Map<String, Any>>()

artifactMap.map { (artifactName, map) ->
    // build jar
    val jar = tasks.create<Jar>(map.getValue("libTaskName")) {
        archiveBaseName.set(artifactName)
        archiveVersion.set(map.getValue("version"))
        description = map.getValue("taskDescription").toString()
        val sourceSet = map.get("sourceSet") ?: "main"
        from(sourceSets[sourceSet].output) {
            include("**")
        }
    }
    val src = tasks.create<Jar>(map.getValue("srcTaskName")) {
        archiveBaseName.set(artifactName)
        archiveClassifier.set("sources")
        archiveVersion.set(map.getValue("version"))
        description = map.getValue("taskDescription").toString()
        val sourceSet = map.get("sourceSet") ?: "main"
        from(sourceSets[sourceSet].allSource) {
            include("**")
        }
    }
    val dokka = tasks.create<org.jetbrains.dokka.gradle.DokkaTask>("dokka-" + map.getValue("srcTaskName")) {
        val dokkaOutDir = buildDir.resolve("dokka/${map.get("sourceSet") ?: "main"}")
        outputDirectory.set(dokkaOutDir)
        dokkaSourceSets {
            named(map.get("sourceSet") ?: "main") { // Or source set name, for single-platform the default source sets are `main` and `test`
                // Used to remove a source set from documentation, test source sets are suppressed by default
                suppress.set(false)

                // Use to include or exclude non public members
                includeNonPublic.set(false)

                // Do not output deprecated members. Applies globally, can be overridden by packageOptions
                skipDeprecated.set(false)

                // Emit warnings about not documented members. Applies globally, also can be overridden by packageOptions
                reportUndocumented.set(true)

                // Do not create index pages for empty packages
                skipEmptyPackages.set(true)

                // This name will be shown in the final output
                displayName.set("JVM")

                // Platform used for code analysis. See the "Platforms" section of this readme
                platform.set(org.jetbrains.dokka.Platform.jvm)

                // Set the sourceRoots for the artifact
                val realSource = file("$rootDir/src/${map.get("sourceSet") ?: "main"}/kotlin")
                sourceRoots.from(realSource)

                // Specifies the location of the project source code on the Web.
                // If provided, Dokka generates "source" links for each declaration.
                // Repeat for multiple mappings
                sourceLink {
                    // Unix based directory relative path to the root of the project (where you execute gradle respectively).
                    localDirectory.set(file("src/${map.get("sourceSet") ?: "main"}/kotlin"))

                    // URL showing where the source code can be accessed through the web browser
                    remoteUrl.set(
                        uri(
                            "https://github.com/naviothera/casper/tree/master/src/${map.get("sourceSet") ?: "main"}/kotlin"
                        ).toURL()
                    )
                    // Suffix which is used to append the line number to the URL. Use #L for GitHub
                    remoteLineSuffix.set("#L")
                }

                // Used for linking to JDK documentation
                jdkVersion.set(11)
                // Disable linking to online kotlin-stdlib documentation
                noStdlibLink.set(false)
                // Disable linking to online JDK documentation
                noJdkLink.set(false)
                // Include generated files in documentation
                // By default Dokka will omit all files in folder named generated that is a child of buildDir
                suppressGeneratedFiles.set(false)
            }
        }
    }
    val docs = tasks.create<Jar>("javadoc-" + map.getValue("srcTaskName")) {
        dependsOn(dokka)
        archiveBaseName.set(artifactName)
        archiveClassifier.set("javadoc")
        archiveVersion.set(map.getValue("version"))
        description = map.getValue("taskDescription").toString()
        from(dokka.outputDirectory) {
            include("**")
        }
    }
    // prepare publications map
    publicationsMap.put(
        map.getValue("publicationName"),
        mapOf(
            "artifactId" to artifactName,
            "library" to jar,
            "src" to src,
            "docs" to docs,
            "artifactVersion" to map.getValue("version"),
            "groupId" to map.getValue("groupId")
        )
    )
}

publishing {
    publications {
        repositories {
            mavenLocal()
            maven(System.getenv("GITLAB_MAVEN_REPO_URL") ?: "") {
                credentials {
                    username = "Deploy-Token"
                    password = System.getenv("GITLAB_MAVEN_REPO_PASSWORD")
                }
            }
        }
        publicationsMap.forEach { (name, publication) ->
            create(name, MavenPublication::class) {
                groupId = publication.getValue("groupId").toString()
                version = publication.getValue("artifactVersion").toString()
                artifactId = publication.getValue("artifactId").toString()
                artifact(publication.getValue("library"))
                artifact(publication.getValue("src"))
                artifact(publication.getValue("docs"))
                pom {
                    this.description.set("A library for testing Spring GraphQL endpoints backed by Postgres with per-test isolation and shared database templating.")
                    this.url.set("https://github.com/naviothera/casper")
                    licenses {
                        license {
                            this.name.set("The Apache License, Version 2.0")
                            this.url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            this.name.set("Zack Radick")
                            this.id.set("zradick")
                            this.email.set("zradick@navio.com")
                        }
                    }
                    scm {
                        this.connection.set("scm:git:git@github.com:naviothera/casper.git")
                        this.developerConnection.set("scm:git:git@github.com:naviothera/casper.git")
                        this.url.set("https://github.com/naviothera/casper")
                    }
                }
            }
        }
    }
}
/*
signing {
    sign(publishing.publications["mavenJava"])
}*/
