plugins {
    `java-library`
    checkstyle
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

version = "1.2.1"

val currentBuildNumber by extra("1234")

// tag::repositories[]
repositories {
    mavenCentral()
}
// end::repositories[]

// tag::compile-dependencies[]
dependencies {
    implementation("log4j:log4j:1.2.12")  // <1>
}
// end::compile-dependencies[]

// tag::pom-dependencies[]
dependencies {
    testImplementation("org.codehaus.groovy:groovy-all:2.5.4")
}
// end::pom-dependencies[]

// tag::process-resources[]
tasks {
    processResources {
        expand("version" to version, "buildNumber" to currentBuildNumber)
    }
}
// end::process-resources[]

// tag::checkstyle[]
checkstyle {
    config = resources.text.fromFile("checkstyle.xml", "UTF-8")
    isShowViolations = true
    isIgnoreFailures = false
}
// end::checkstyle[]

// tag::depends-on[]
tasks {
    test {
        mustRunAfter(checkstyleMain, checkstyleTest)
    }
}
// end::depends-on[]
