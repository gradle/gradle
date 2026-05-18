// tag::automatic-classpath[]
plugins {
    groovy
    `java-gradle-plugin`
}

dependencies {
    testImplementation("org.spockframework:spock-core:2.4-groovy-4.0") {
        exclude(group = "org.apache.groovy")
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
// end::automatic-classpath[]

tasks.named<Test>("test") {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
}
