plugins {
    java
}

repositories {
    mavenCentral()
}

// tag::locking-one[]
configurations.getByName("compileClasspath") {
    resolutionStrategy.activateDependencyLocking()
}
// end::locking-one[]

// tag::locking-deps[]
dependencies {
    implementation("org.springframework:spring-beans:[5.0,6.0)")
}
// end::locking-deps[]
