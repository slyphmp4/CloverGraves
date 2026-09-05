plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.slyph"
version = "2.0.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.110-stable")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        isTransitive = false
    }
    compileOnly("me.clip:placeholderapi:2.12.3")

    implementation("org.bstats:bstats-bukkit:3.2.1")
    implementation("com.h2database:h2:2.4.240")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("com.mysql:mysql-connector-j:26.7.0")

    testImplementation(platform("org.junit:junit-bom:5.14.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:26.2.build.110-stable")
    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") {
        isTransitive = false
    }
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
    mergeServiceFiles()
    relocate("org.bstats", "com.slyph.clovergraves.libs.bstats")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
