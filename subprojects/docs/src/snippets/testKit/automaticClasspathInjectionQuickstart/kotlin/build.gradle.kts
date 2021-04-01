// tag::automatic-classpath[]
plugins {
    groovy
    `java-gradle-plugin`
}

dependencies {
    testImplementation("org.spockframework:spock-core:2.0-M5-groovy-3.0") {
        exclude(group = "org.codehaus.groovy")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api")
}
// end::automatic-classpath[]

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
}
