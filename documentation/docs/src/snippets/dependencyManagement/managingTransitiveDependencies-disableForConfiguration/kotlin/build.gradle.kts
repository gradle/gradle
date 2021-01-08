plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::transitive-per-configuration[]
configurations.all {
    isTransitive = false
}

dependencies {
    implementation("com.google.guava:guava:23.0")
}
// end::transitive-per-configuration[]

tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into(layout.buildDirectory.dir("libs"))
}
