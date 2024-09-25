plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.shadow)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.versions)
}

group = "dev.limebeck"
version = "0.0.1"

application {
    mainClass.set("dev.limebeck.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.coroutines)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.apache)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.logback)
    implementation(libs.slf4j)

    implementation(libs.hoplite.core)
    implementation(libs.hoplite.yaml)
    implementation(libs.suspendapp)
    
    implementation(libs.redis)

    testImplementation(libs.kotlin.junit)
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        includeTags("unit")
    }
}

tasks.register<Test>("testIntegration") {
    group = "verification"
    description = "Run integration tests only"

    useJUnitPlatform {
        includeTags("integration")
    }
}

tasks.withType<Jar>().configureEach {
    doLast {
        logger.info(
            """
            ===========================================================
            Build success for project: ${project.name} version - ${project.version}
            ===========================================================
            """.trimIndent(),
        )
    }
}