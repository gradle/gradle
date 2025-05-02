plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::producer[]
group = "org.gradle.demo"

sourceSets {
    create("mysqlSupport") {
        java {
            srcDir("src/mysql/java")
        }
    }
}

java {
    registerFeature("mysqlSupport") {
        usingSourceSet(sourceSets["mysqlSupport"])
    }
}

dependencies {
    "mysqlSupportImplementation"("mysql:mysql-connector-java:8.0.14")
}
// end::producer[]
