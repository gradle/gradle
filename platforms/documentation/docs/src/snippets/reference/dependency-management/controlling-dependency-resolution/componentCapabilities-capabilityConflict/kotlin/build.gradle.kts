plugins {
    id("java")
}

repositories {
    mavenCentral()
}

// tag::capability-conflict[]
dependencies {
    implementation("com.zaxxer:HikariCP:4.0.3")  // A popular connection pool
    implementation("org.apache.commons:commons-dbcp2:2.8.0")  // Another connection pool
}

configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("database:connection-pool") {
        select("com.zaxxer:HikariCP")
    }
}
// end::capability-conflict[]
