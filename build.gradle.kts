plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
    jacoco
}

group = "com.apimocktle"
version = "2.3.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2023.3")
        bundledPlugins("com.intellij.java", "org.jetbrains.idea.maven", "org.jetbrains.plugins.gradle", "org.jetbrains.kotlin", "org.intellij.groovy", "org.intellij.intelliLang")
        plugin("org.intellij.scala:2023.3.19")
        pluginVerifier()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Plugin.Java)
    }

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.12.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.12.2")
    implementation("org.xerial:sqlite-jdbc:3.34.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("com.google.guava:guava:33.0.0-jre")
    testImplementation("com.google.guava:failureaccess:1.0.2")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test>().configureEach {
    useJUnit()
    maxParallelForks = 1
    testLogging {
        events("started", "passed", "failed", "skipped")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    // Ensure JaCoCo agent is attached to the forked test JVM
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.apimocktle.plugin"
        name = "ApiMocktle"
        version = project.version.toString()
        description = file("src/main/resources/pluginDescription.html").readText()
        changeNotes = provider {
            val lines = file("CHANGELOG.md").readLines()
            val start = lines.indexOfFirst { it.startsWith("## [") }
            val end = lines.drop(start + 1).indexOfFirst { it.startsWith("## [") }
            val section = if (end >= 0) lines.subList(start, start + 1 + end) else lines.drop(start)
            section.joinToString("<br/>")
                .replace("### ", "<h3>")
                .replace("<br/>- ", "<br/>• ")
        }
        ideaVersion {
            sinceBuild = "233"
            untilBuild = provider { null }
        }
    }

    sandboxContainer = layout.projectDirectory.dir("idea-sandbox")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main"))
    )
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/*.exec") }
    )
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/report.xml"))
        html.required.set(true)
    }
}

// Mock Agent JAR 自动打包到插件 ZIP 中
val agentJarPath = project(":mock-agent").layout.buildDirectory.file("libs/mock-agent.jar")

// 1. 复制到 sandbox（开发调试 runIde 用）
val copyAgentJarToSandbox by tasks.registering(Copy::class) {
    dependsOn(":mock-agent:shadowJar")
    from(agentJarPath)
    mustRunAfter("prepareSandbox")
    into(file("${layout.buildDirectory.get().asFile}/idea-sandbox/plugins-test/ApiMocktle/lib"))
}

// 2. 用 ant 追加 Agent JAR 到 ZIP（update=true，不重新压缩已有条目）
val injectAgentJarToZip by tasks.registering {
    dependsOn("buildPlugin", ":mock-agent:shadowJar")
    val zipFile = layout.buildDirectory.file("distributions/ApiMocktle-${project.version}.zip").get().asFile
    val agentFile = agentJarPath.get().asFile

    doLast {
        if (!zipFile.exists() || !agentFile.exists()) return@doLast
        ant.withGroovyBuilder {
            "zip"("destfile" to zipFile.absolutePath, "update" to true) {
                "zipfileset"("dir" to agentFile.parentFile.absolutePath, "prefix" to "ApiMocktle/lib") {
                    "include"("name" to agentFile.name)
                }
            }
        }
        println("[injectAgentJarToZip] Added ${agentFile.name} to ${zipFile.name}")
    }
}

tasks.named("prepareSandbox") {
    finalizedBy(copyAgentJarToSandbox)
}

tasks.named("buildPlugin") {
    finalizedBy(injectAgentJarToZip)
}

// buildSearchableOptions 在部分环境下会崩溃导致产物损坏，跳过
tasks.named("buildSearchableOptions") {
    enabled = false
}
