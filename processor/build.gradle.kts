plugins {
    kotlin("jvm")
}

group = "org.jgwiz"
version = "unspecified"

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