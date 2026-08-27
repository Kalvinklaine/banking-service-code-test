plugins {
    kotlin("jvm") version "2.1.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("banking.MainKt")
    applicationName = "banking-service"
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}