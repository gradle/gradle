plugins {
    id("scala")
}

version = "1.0"

repositories {
    mavenCentral()
}

scala {
    scalaVersion = "2.13.12"
}

dependencies {
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
