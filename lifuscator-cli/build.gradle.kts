plugins {
    id("java")
}

group = "org.lifuscator"
version = "1.0"

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.lifuscator.cli.Main"
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // lifuscator core
    implementation(project(":lifuscator-core"))
}

tasks.test {
    useJUnitPlatform()
}