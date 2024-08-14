plugins {
    java
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven {
        url = uri("${projectDir}/../lib-a/build/publishing-repository/")
    }
    maven {
        url = uri("${projectDir}/../lib-b/build/publishing-repository/")
    }
}

// tag::java-dependency-mgmt[]
dependencies {
    implementation("com.example:lib-a:1.0")
    implementation("com.example:lib-b:1.0")
    implementation("com.google.guava:guava:33.2.1-jre")
}

configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("com.example:logging") {
        select("com.example:lib-b:1.0")
    }
}
// end::java-dependency-mgmt[]
