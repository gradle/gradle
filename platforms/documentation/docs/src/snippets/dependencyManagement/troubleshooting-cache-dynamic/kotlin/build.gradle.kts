plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::dynamic[]
dependencies {
    implementation("org.springframework:spring-web:5.+")
}
// end::dynamic[]

// tag::dynamic-version-cache-control[]
configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor(10, "minutes")
}
// end::dynamic-version-cache-control[]

tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into(layout.buildDirectory.dir("libs"))
}
