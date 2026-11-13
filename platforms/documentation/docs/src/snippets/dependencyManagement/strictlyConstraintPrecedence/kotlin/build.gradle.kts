plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::strictly-precedence-root[]
dependencies {
    api(project(":moduleA"))
    api("org.apache.httpcomponents:httpclient") {
        version {
            strictly("4.4")  // This constraint is honored
        }
    }
}
// end::strictly-precedence-root[]

tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into(layout.buildDirectory.dir("libs"))
}
