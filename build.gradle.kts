plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.0"
}

group = "com.github.gquad.camelgraph"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}

// Configure Gradle IntelliJ Plugin
intellij {
    version.set("2023.2") // Target IDE version - compatible with 2023.2+
    type.set("IC") // Target IDE type - IC (IntelliJ Community)

    plugins.set(listOf("com.intellij.java"))
}

tasks {
    // Set the JVM compatibility for the task
    // Using Java 17 for compatibility with IntelliJ 2023.2
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("232") // IntelliJ IDEA 2023.2
        untilBuild.set("242.*") // Up to IntelliJ IDEA 2024.2
    }

    // Configurações opcionais para publicação no JetBrains Marketplace
    // Descomente apenas se você for publicar o plugin no Marketplace
    /*
    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
    */
}
