plugins {
    java
}

repositories {
    mavenCentral()
}

// tag::exclude-transitive-dependencies[]
configurations {
    "implementation" {
        exclude(group = "commons-collections", module = "commons-collections")
    }
}

dependencies {
    implementation("commons-beanutils:commons-beanutils:1.9.4")
    implementation("com.opencsv:opencsv:4.6")
}
// end::exclude-transitive-dependencies[]

tasks.register("printArtifacts") {
    val runtimeClasspath: FileCollection = configurations.runtimeClasspath.get()
    doLast {
        runtimeClasspath.forEach { println(it.name) }
    }
}
