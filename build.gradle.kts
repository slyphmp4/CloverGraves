import java.util.zip.ZipFile

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.slyph"
version = "2.1.1"

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
    implementation("com.h2database:h2:2.5.250")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("com.mysql:mysql-connector-j:26.7.0")
    implementation("com.zaxxer:HikariCP:7.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    testImplementation(platform("org.junit:junit-bom:5.14.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:26.2.build.110-stable")
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
    options.compilerArgs.add("-Xlint:deprecation")
}

val pluginVersion = version.toString()
tasks.processResources {
    inputs.property("pluginVersion", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
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
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    relocate("org.bstats", "com.slyph.clovergraves.libs.bstats")
    relocate("com.zaxxer.hikari", "com.slyph.clovergraves.libs.hikari")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

val releaseJar = tasks.shadowJar.flatMap { it.archiveFile }
val verifyReleaseJar = tasks.register("verifyReleaseJar") {
    dependsOn(tasks.shadowJar)
    inputs.file(releaseJar)
    doLast {
        ZipFile(releaseJar.get().asFile).use { jar ->
            val entry = checkNotNull(jar.getEntry("META-INF/services/java.sql.Driver"))
            val drivers = jar.getInputStream(entry).bufferedReader().use { it.readLines().toSet() }
            check(drivers.containsAll(setOf("org.h2.Driver", "org.sqlite.JDBC", "com.mysql.cj.jdbc.Driver"))) {
                "Release JAR must include all three JDBC service providers: $drivers"
            }
        }
    }
}

tasks.check {
    dependsOn(verifyReleaseJar)
}
