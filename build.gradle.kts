import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
}

group = "io.github.jean202"
version = "0.1.0-SNAPSHOT"

subprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:5.12.1"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
