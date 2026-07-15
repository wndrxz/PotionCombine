plugins {
    java
}

group = "dev.wndrxz"
version = "1.3.1"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    // Soft-depend at runtime; we never bundle it.
    compileOnly("me.clip:placeholderapi:2.11.6")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // Gradle 9 wants the platform launcher on the test runtime classpath.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The tests compile against the same Paper API the plugin does — the
// matcher touches Material and PotionType and mocking those is worse
// than just having them on the classpath.
configurations {
    testImplementation {
        extendsFrom(configurations.compileOnly.get())
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    // plugin.yml is the only resource with ${version} in it.
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("PotionCombine")
}
