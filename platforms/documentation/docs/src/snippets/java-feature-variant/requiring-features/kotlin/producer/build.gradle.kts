plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::producer[]
group = "org.gradle.demo"

java {
    registerFeature("mysqlSupport") {
        usingSourceSet(sourceSets["main"])
    }
}

dependencies {
    "mysqlSupportImplementation"("mysql:mysql-connector-java:8.0.14")
}
// end::producer[]
