// tag::create-uber-jar-example[]
plugins {
    java
}

version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("commons-io:commons-io:2.6")
}

task<Jar>("uberJar") {
    appendix = "uber"

    from(sourceSets["main"].output)
    from(
        configurations.runtimeClasspath
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    )
}
// end::create-uber-jar-example[]
