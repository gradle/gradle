plugins {
    scala
}

version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.12")
    testImplementation("junit:junit:4.13")
}

// tag::custom-source-locations[]
sourceSets {
    main {
        scala {
            setSrcDirs(listOf("src/scala"))
        }
    }
    test {
        scala {
            setSrcDirs(listOf("test/scala"))
        }
    }
}
// end::custom-source-locations[]
