import java.io.File

plugins {
    java
}

project.setProperty("sourceCompatibility", "1.8")
project.setProperty("targetCompatibility", "1.8")

version = "1.2.1"

repositories {
    jcenter()
}

dependencies {
    testImplementation("junit:junit:4.12")
}

// tag::custom-src-dirs[]
sourceSets {
    getByName("main") {
        java {
            setSrcDirs(listOf("src"))
        }
    }

    getByName("test") {
        java {
            setSrcDirs(listOf("test"))
        }
    }
}
// end::custom-src-dirs[]

// tag::custom-extra-src-dir[]
sourceSets {
    getByName("main") {
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
project.setProperty("testResultsDirName", "$buildDir/my-test-results")

tasks.create("showDirs") {
    doLast {
        logger.quiet(rootDir.toPath().relativize((project.properties["reportsDir"] as File).toPath()).toString())
        logger.quiet(rootDir.toPath().relativize((project.properties["testResultsDir"] as File).toPath()).toString())
    }
}
// end::custom-report-dirs[]
