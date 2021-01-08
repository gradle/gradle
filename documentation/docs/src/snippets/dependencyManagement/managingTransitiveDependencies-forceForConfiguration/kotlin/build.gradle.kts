plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::force-per-configuration[]
configurations {
    "compileClasspath" {
        resolutionStrategy.force("commons-codec:commons-codec:1.9")
    }
}

dependencies {
    implementation("org.apache.httpcomponents:httpclient:4.5.4")
}
// end::force-per-configuration[]

tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into(layout.buildDirectory.dir("libs"))
}
