plugins {
    java
    id("org.springframework.boot") version "3.5.16"
}

group = "de.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.liquibase:liquibase-core")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Uses existing Node/pnpm installations; never downloads a toolchain.
val frontendDirectory = layout.projectDirectory.dir("frontend")
val pnpmExecutable = providers.gradleProperty("pnpmExecutable").orElse("pnpm")
val frontendDev = providers.gradleProperty("frontendDev").map(String::toBoolean).orElse(false)

val frontendInstall by tasks.registering(Exec::class) {
    workingDir(frontendDirectory)
    commandLine(pnpmExecutable.get(), "install", "--frozen-lockfile")
    inputs.files("frontend/package.json", "frontend/pnpm-lock.yaml", "frontend/.npmrc", "frontend/.node-version")
    outputs.dir("frontend/node_modules")
}
val frontendBuild by tasks.registering(Exec::class) {
    dependsOn(frontendInstall)
    workingDir(frontendDirectory)
    commandLine(pnpmExecutable.get(), "run", "build")
    inputs.files(fileTree("frontend/src"), "frontend/index.html", "frontend/tsconfig.json", "frontend/vite.config.ts", "frontend/package.json", "frontend/pnpm-lock.yaml")
    outputs.dir("frontend/dist")
}
val frontendTest by tasks.registering(Exec::class) {
    dependsOn(frontendInstall)
    workingDir(frontendDirectory)
    commandLine(pnpmExecutable.get(), "run", "test")
}
tasks.processResources {
    if (!frontendDev.get()) {
        dependsOn(frontendBuild)
        from(frontendDirectory.dir("dist")) { into("static") }
    }
}
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    if (frontendDev.get()) systemProperty("spring.profiles.active", "frontend-dev")
}
tasks.check { dependsOn(frontendTest) }
