// tag::automatic-classpath[]
plugins {
    groovy
    `java-gradle-plugin`
}

dependencies {
    testImplementation("org.spockframework:spock-core:1.3-groovy-2.4") {
        exclude(module = "groovy-all")
    }
}
// end::automatic-classpath[]

repositories {
    mavenCentral()
}
