plugins {
    java
    `groovy-base`
}

version = "1.0.2"
group = "org.gradle.sample"

repositories {
    jcenter()
}

dependencies {
    implementation("org.codehaus.groovy:groovy-all:3.0.5")
    testImplementation("org.spockframework:spock-core:2.0-M3-groovy-3.0")
}
