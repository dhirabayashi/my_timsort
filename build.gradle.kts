plugins {
    kotlin("jvm") version "2.4.21"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
    // n = 2^26 のランスタック最悪ケースのため
    maxHeapSize = "4g"
    // 比較回数レポートを毎回出すため、入力が変わっていなくても常に再実行する
    outputs.upToDateWhen { false }
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

// ./gradlew benchmark [-Pn=1000000] [-Ppatterns=random,ascending]
tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "各実装の実行時間を JDK と比較する"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "timsort.BenchmarkKt"
    maxHeapSize = "4g"
    args(
        project.findProperty("n")?.toString() ?: "",
        project.findProperty("patterns")?.toString() ?: "",
    )
}
