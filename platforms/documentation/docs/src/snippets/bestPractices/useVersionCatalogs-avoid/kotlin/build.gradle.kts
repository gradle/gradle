// tag::avoid-this[]
plugins {
    id("java-library")
    id("com.github.ben-manes.versions").version("0.45.0")
}
// end::avoid-this[]

repositories {
    mavenCentral()
}

// tag::avoid-this[]
val groovyVersion = "3.0.5"

dependencies {
    api("org.codehaus.groovy:groovy:$groovyVersion")
    api("org.codehaus.groovy:groovy-json:$groovyVersion")
    api("org.codehaus.groovy:groovy-nio:$groovyVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

dependencies {
    implementation("org.apache.commons:commons-lang3") {
        version {
            strictly("[3.8, 4.0[")
            prefer("3.9")
        }
    }
}
// end::avoid-this[]
