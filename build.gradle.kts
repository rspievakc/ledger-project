import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
    jacoco
}

group = "com.teya"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // assertj-core arrives transitively via spring-boot-starter-test (BOM-managed).
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf(
                "com.teya.ledger.domain",
                "com.teya.ledger.domain.account",
                "com.teya.ledger.domain.customer",
                "com.teya.ledger.domain.error",
                "com.teya.ledger.application"
            )
            limit {
                counter = "LINE"
                minimum = "0.95".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

openApi {
    val port = (project.findProperty("openApiPort") as String?)?.toInt() ?: 8080
    apiDocsUrl.set("http://localhost:$port/v3/api-docs.yaml")
    outputDir.set(file("$projectDir/docs"))
    outputFileName.set("openapi.yaml")
    customBootRun {
        args.set(listOf("--server.port=$port"))
    }
}

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("teya-ledger:${project.version}")
    builder.set("paketobuildpacks/builder-jammy-tiny")
    environment.set(
        mapOf(
            "BP_JVM_VERSION" to "25"
        )
    )
}
