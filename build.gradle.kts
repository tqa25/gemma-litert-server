plugins {
    application
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}


dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-jvm:latest.release")
}

application {
    mainClass.set("dev.gemma.server.Main")
}
