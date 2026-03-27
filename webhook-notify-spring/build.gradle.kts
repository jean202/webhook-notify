plugins {
    `java-library`
}

dependencies {
    api(project(":webhook-notify-core"))
    compileOnly("org.springframework:spring-context:6.2.3")
}
