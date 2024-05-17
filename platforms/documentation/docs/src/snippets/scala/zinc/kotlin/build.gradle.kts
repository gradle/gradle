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

// tag::adjust-memory[]
tasks.withType<ScalaCompile>().configureEach {
    scalaCompileOptions.forkOptions.apply {
        memoryMaximumSize = "1g"
        jvmArgs = listOf("-XX:MaxMetaspaceSize=512m")
    }
}
// end::adjust-memory[]
