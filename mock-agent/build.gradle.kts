plugins {
    kotlin("jvm") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.apimocktle"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    // Byte Buddy - shade 避免与 IntelliJ 内置版本冲突
    implementation("net.bytebuddy:byte-buddy:1.14.18")
    implementation("net.bytebuddy:byte-buddy-agent:1.14.18")

    // Jackson - JSON 序列化
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.12.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.12.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.2")

    // 测试
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
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
}

// Shadow jar: relocate Byte Buddy 避免类加载冲突
tasks.shadowJar {
    relocate("net.bytebuddy", "com.apimocktle.agent.buddy")
    archiveFileName.set("mock-agent.jar")
    archiveClassifier.set("")
    manifest {
        attributes(
            "Premain-Class" to "com.apimocktle.agent.MockAgentMain",
            "Agent-Class" to "com.apimocktle.agent.MockAgentMain",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true",
            "Can-Set-Native-Method-Prefix" to "true",
        )
    }
}

// 使 build 任务依赖 shadowJar
tasks.build { dependsOn(tasks.shadowJar) }
