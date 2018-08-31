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

task<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into("$buildDir/libs")
}
