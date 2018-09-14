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

task("configureUberJar") {
    doLast {
        // Don't do this during configuration phase!
        tasks.getByName<Jar>("uberJar")
            .from(configurations.runtimeClasspath.filter { it.name.endsWith("jar") }.map { zipTree(it) })
    }
}

task<Jar>("uberJar") {
    appendix = "uber"
    dependsOn("configureUberJar")

    from(sourceSets["main"].output)
}
// end::create-uber-jar-example[]
