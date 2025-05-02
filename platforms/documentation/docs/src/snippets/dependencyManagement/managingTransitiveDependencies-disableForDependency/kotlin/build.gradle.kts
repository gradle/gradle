plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::transitive-per-dependency[]
dependencies {
    implementation("com.google.guava:guava:23.0") {
        isTransitive = false
    }
}
// end::transitive-per-dependency[]

tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into(layout.buildDirectory.dir("libs"))
}
