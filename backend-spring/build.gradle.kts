plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.obsidian"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Compile against the Java 21 API even when Gradle itself runs on a newer JDK.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.session:spring-session-data-redis")
    // Jakarta Mail, used only to parse raw Gmail messages (no SMTP).
    implementation("org.eclipse.angus:angus-mail")
    // Spring Security's WebAuthn support needs WebAuthn4J on the classpath.
    implementation("com.webauthn4j:webauthn4j-core:0.29.7.RELEASE")
    // Argon2PasswordEncoder needs BouncyCastle.
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.awaitility:awaitility")
    // Real PostgreSQL 16 and an in-process Redis for tests and `bootTestRun`, so
    // neither needs Docker. The Postgres binaries come from Maven Central.
    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:16.15.0"))
    testImplementation("com.github.fppt:jedis-mock:1.1.21") {
        // A second org.json next to spring-boot-test's android-json.
        exclude(group = "org.json", module = "json")
    }
    // Emulated FIDO2 authenticator for the passkey ceremony tests. Its BouncyCastle
    // (jdk15to18) is swapped for the jdk18on build the app already uses.
    testImplementation("com.webauthn4j:webauthn4j-test:0.29.7.RELEASE") {
        exclude(group = "org.bouncycastle")
    }
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.86")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Mockito attaches its agent at runtime.
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

// `gradle bootTestRun`: the backend on :8080 with embedded Postgres + Redis (no Docker).
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootTestRun") {
    mainClass.set("com.obsidian.DevServer")
}
