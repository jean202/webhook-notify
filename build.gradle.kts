import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.plugins.signing.SigningExtension

plugins {
    base
}

group = "io.github.jean202"
version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0")

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        // Maven Central requires sources and javadoc artifacts.
        withSourcesJar()
        withJavadocJar()
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:5.12.1"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                pom {
                    name.set(project.name)
                    description.set("Lightweight webhook notification SDK for Java and Spring applications.")
                    url.set("https://github.com/jean202/webhook-notify")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("jean202")
                            name.set("jean202")
                            url.set("https://github.com/jean202")
                        }
                    }
                    scm {
                        url.set("https://github.com/jean202/webhook-notify")
                        connection.set("scm:git:https://github.com/jean202/webhook-notify.git")
                        developerConnection.set("scm:git:ssh://git@github.com/jean202/webhook-notify.git")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/jean202/webhook-notify")
                credentials {
                    username = providers.gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                        .orNull
                    password = providers.gradleProperty("gpr.key")
                        .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                        .orNull
                }
            }
        }
    }

    // Signing is required by Maven Central but not by GitHub Packages, so it stays
    // opt-in: it only activates when a key is supplied via ORG_GRADLE_PROJECT_signingKey.
    extensions.configure<SigningExtension> {
        val signingKey = providers.environmentVariable("ORG_GRADLE_PROJECT_signingKey").orNull
        val signingPassword = providers.environmentVariable("ORG_GRADLE_PROJECT_signingPassword").orNull
        isRequired = signingKey != null
        if (signingKey != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(extensions.getByType<PublishingExtension>().publications["maven"])
        }
    }
}
