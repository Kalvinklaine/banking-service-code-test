plugins {
    kotlin("jvm") version "2.1.20"
    application
}

group = "dinar.interview.example"
version = "1.0-SNAPSHOT"

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