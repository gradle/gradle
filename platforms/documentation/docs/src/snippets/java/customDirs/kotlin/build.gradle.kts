plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

version = "1.2.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13")
}

// tag::custom-src-dirs[]
sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))
        }
    }

    test {
        java {
            setSrcDirs(listOf("test"))
        }
    }
}
// end::custom-src-dirs[]

// tag::custom-extra-src-dir[]
sourceSets {
    main {
        java {
            srcDir("thirdParty/src/main/java")
        }
    }
}
// end::custom-extra-src-dir[]


// tag::custom-source-set[]
sourceSets {
    create("intTest")
}
// end::custom-source-set[]

// tag::custom-report-dirs[]
reporting.baseDir = file("my-reports")
java.testResultsDir = layout.buildDirectory.dir("my-test-results")

tasks.register("showDirs") {
    val rootDir = project.rootDir
    val reportsDir = project.reporting.baseDirectory
    val testResultsDir = project.java.testResultsDir

    doLast {
        logger.quiet(rootDir.toPath().relativize(reportsDir.get().asFile.toPath()).toString())
        logger.quiet(rootDir.toPath().relativize(testResultsDir.get().asFile.toPath()).toString())
    }
}
// end::custom-report-dirs[]
