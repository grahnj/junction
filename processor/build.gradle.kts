plugins {
    kotlin("jvm")
    id("maven-publish")
}

group = "com.github.grahnj"
version = "0.3.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":library"))
    implementation(libs.kspSymbolProcessing)
//    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("junction") {
            from(components["java"])
            artifactId = "junction"
        }
    }
}