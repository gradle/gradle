// tag::automatic-classpath[]
plugins {
    groovy
    `java-gradle-plugin`
}

dependencies {
    testImplementation("org.spockframework:spock-core:2.0-M4-groovy-3.0") {
        exclude(module = "groovy-all")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api")
}
// end::automatic-classpath[]

repositories {
    mavenCentral()
}
