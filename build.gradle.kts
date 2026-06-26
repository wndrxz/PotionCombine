plugins {
    java
}

group = "dev.wndrxz"
version = "1.3.0"

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
    // Исправление для Gradle 9.0+: явный запуск платформы JUnit
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations {
    // Paper API is needed at test runtime too so the matcher tests
    // can touch Material / PotionType enums.
    testImplementation { extendsFrom(configurations.compileOnly.get()) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("PotionCombine")
    archiveClassifier.set("")
}