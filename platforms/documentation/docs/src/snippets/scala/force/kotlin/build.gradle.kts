plugins {
    id("scala")
}

repositories {
    mavenCentral()
}

scala {
    scalaVersion = "2.13.12"
}

dependencies {
    implementation("commons-collections:commons-collections:3.2.2")
    testImplementation("junit:junit:4.13")
}

// tag::force-compilation[]
tasks.withType<ScalaCompile>().configureEach {
    scalaCompileOptions.apply {
        isForce = true
    }
}
// end::force-compilation[]
