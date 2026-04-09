plugins {
    `java-library`
}

dependencies {
    api(project(":webhook-notify-core"))
    compileOnly("org.springframework:spring-context:6.2.3")
    compileOnly("org.springframework:spring-aop:6.2.3")
    compileOnly("org.aspectj:aspectjweaver:1.9.22")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.4.3")

    testImplementation("org.springframework:spring-context:6.2.3")
    testImplementation("org.springframework:spring-aop:6.2.3")
    testImplementation("org.aspectj:aspectjweaver:1.9.22")
    testImplementation("org.springframework:spring-test:6.2.3")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:3.4.3")
    testImplementation("org.springframework.boot:spring-boot-test:3.4.3")
    testImplementation(project(":webhook-notify-test"))
}
