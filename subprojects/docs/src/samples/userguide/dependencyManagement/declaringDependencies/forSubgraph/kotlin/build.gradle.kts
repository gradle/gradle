plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::dependencies-for-subgraph[]
dependencies {
    implementation("org.apache.hadoop:hadoop-common:3.2.0") // depends on 'commons-io:commons-io:2.5'
    implementation("commons-io:commons-io:2.4") {
        version { forSubgraph() }
    }
}
// end::dependencies-for-subgraph[]

tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into("$buildDir/libs")
}
