plugins {
    java
}

group = "fr.darkvodou"
version = "0.2.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.16.1") {
        // ANTLR parser generator (flagged as vulnerable, no fixed version available): not needed to compile
        exclude(group = "org.antlr", module = "antlr4")
    }
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.16.1") { isTransitive = false }

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Patched versions of vulnerable transitive dependencies
    constraints {
        compileOnly("org.codehaus.plexus:plexus-utils:3.6.1") {
            because("CVE-2025-67030 (path traversal in Expand.extractFile)")
        }
        compileOnly("org.apache.commons:commons-lang3:3.18.0") {
            because("CVE-2025-48924 (uncontrolled recursion)")
        }
        compileOnly("org.apache.logging.log4j:log4j-api:2.25.5") {
            because("CVE-2026-49844 (MapMessage JSON encoding)")
        }
    }
}

// Tests run against the same Paper and FAWE APIs the plugin is compiled with
configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

// "MoreTools" group: common tasks gathered in the IDE's Gradle panel
val moreToolsGroup = "MoreTools"

listOf("clean", "build", "jar", "assemble", "compileJava", "processResources", "check", "test")
    .forEach { name -> tasks.named(name) { group = moreToolsGroup } }

tasks.register("rebuild") {
    group = moreToolsGroup
    description = "Cleans then rebuilds the plugin."
    dependsOn(tasks.clean, tasks.build)
}

tasks.build {
    mustRunAfter(tasks.clean)
}
