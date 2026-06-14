plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.2"
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "dev.lifus.cli.Main"
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // lifus core
    implementation(project(":lifus-core"))
}

tasks.test {
    useJUnitPlatform()
}