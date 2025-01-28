plugins {
    scala
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.12")
}

dependencies {
    implementation("commons-collections:commons-collections:3.2.2")
    testImplementation("junit:junit:4.13")
}

// tag::force-compilation[]
tasks.withType<ScalaCompile>().configureEach {
    scalaCompileOptions.apply {
        force = true
    }
}
// end::force-compilation[]
