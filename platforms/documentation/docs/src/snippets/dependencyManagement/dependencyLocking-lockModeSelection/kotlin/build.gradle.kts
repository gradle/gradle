plugins {
    java
}

repositories {
    mavenCentral()
}

// tag::lock-mode[]
dependencyLocking {
    lockMode = LockMode.STRICT
}
// end::lock-mode[]

configurations.compileClasspath {
    resolutionStrategy.activateDependencyLocking()
}

dependencies {
    implementation("org.springframework:spring-beans:[5.0,6.0)")
}
