plugins {
    kotlin("jvm")
}

group = "com.github.grahnj"
version = "0.2.0"

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