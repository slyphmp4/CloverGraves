plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.slyph"
version = "1.29.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.artillex-studios.com/releases/")
    maven("https://jitpack.io")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.110-stable")
    implementation("com.artillexstudios.axapi:axapi:2.1.8:all")
    compileOnly("org.slf4j:slf4j-api:2.0.17")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        isTransitive = false
    }
    implementation("org.bstats:bstats-bukkit:3.2.1")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:26.2.build.110-stable")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") {
        isTransitive = false
    }
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("it.unimi.dsi:fastutil:8.5.13")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-parameters")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("CloverGraves")
    archiveClassifier.set("unshaded")
}

tasks.shadowJar {
    archiveBaseName.set("CloverGraves")
    archiveClassifier.set("")
    relocate("com.artillexstudios.axapi", "com.slyph.clovergraves.libs.axapi")
    relocate("org.bstats", "com.slyph.clovergraves.libs.bstats")
    relocate("revxrsal.commands", "com.slyph.clovergraves.libs.lamp")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
