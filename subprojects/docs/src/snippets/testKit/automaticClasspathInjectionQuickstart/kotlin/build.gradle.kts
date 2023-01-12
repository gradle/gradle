// tag::automatic-classpath[]
plugins {
    groovy
    `java-gradle-plugin`
}

dependencies {
    testImplementation("org.spockframework:spock-core:2.2-groovy-3.0") {
        exclude(group = "org.codehaus.groovy")
    }
}
// end::automatic-classpath[]

tasks.named<Test>("test") {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
}
