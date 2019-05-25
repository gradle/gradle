plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::force-per-dependency[]
dependencies {
    implementation("org.apache.httpcomponents:httpclient:4.5.4")
    implementation("commons-codec:commons-codec:1.9") {
        isForce = true
    }
}
// end::force-per-dependency[]

tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into("$buildDir/libs")
}
