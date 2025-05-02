plugins {
    java
}

repositories {
    mavenCentral()
}

if (project.hasProperty("sample1")) {
// tag::exclude-transitive-dependencies-1[]
dependencies {
    implementation("commons-beanutils:commons-beanutils:1.9.4") {
        exclude(group = "commons-collections", module = "commons-collections")
    }
}
// end::exclude-transitive-dependencies-1[]
} else if (project.hasProperty("sample2")) {
// tag::exclude-transitive-dependencies-2[]
dependencies {
    implementation("commons-beanutils:commons-beanutils:1.9.4") {
        exclude(group = "commons-collections", module = "commons-collections")
    }
    implementation("com.opencsv:opencsv:4.6") // depends on 'commons-beanutils' without exclude and brings back 'commons-collections'
}
// end::exclude-transitive-dependencies-2[]
} else if (project.hasProperty("sample3")) {
// tag::exclude-transitive-dependencies-3[]
    dependencies {
        implementation("commons-beanutils:commons-beanutils:1.9.4") {
            exclude(group = "commons-collections", module = "commons-collections")
        }
        implementation("com.opencsv:opencsv:4.6") {
            exclude(group = "commons-collections", module = "commons-collections")
        }
    }
// end::exclude-transitive-dependencies-3[]
}

tasks.register("printArtifacts") {
    val runtimeClasspath: FileCollection = configurations.runtimeClasspath.get()
    doLast {
        runtimeClasspath.forEach { println(it.name) }
    }
}
