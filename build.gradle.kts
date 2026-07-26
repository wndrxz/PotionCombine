plugins {
    java
}

group = "dev.wndrxz"
version = "1.5-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
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

// Compile sources as UTF-8 regardless of the platform default, otherwise
// non-ASCII literals (the journal's "• " bullet) come out mangled on
// machines whose default charset isn't UTF-8.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    // plugin.yml is the only resource with ${version} in it.
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("PotionCombine")
}
