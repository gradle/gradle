repositories {
    mavenCentral()
}

val implementation by configurations.creating
val runtimeOnly by configurations.creating

// tag::define-dependency[]
dependencies {
    implementation("org.hibernate:hibernate-core:3.6.7.Final")
}
// end::define-dependency[]

// tag::use-configuration[]
tasks.register("listJars") {
    val implementation: FileCollection = configurations["implementation"]
    doLast {
        implementation.forEach { file: File -> println(file.name) }
    }
}
// end::use-configuration[]

// tag::module-dependencies[]
dependencies {
    runtimeOnly(group = "org.springframework", name = "spring-core", version = "2.5")
    runtimeOnly("org.springframework:spring-aop:2.5")
    runtimeOnly("org.hibernate:hibernate:3.0.5") {
        isTransitive = true
    }
    runtimeOnly(group = "org.hibernate", name = "hibernate", version = "3.0.5") {
        isTransitive = true
    }
}
// end::module-dependencies[]

// tag::file-dependencies[]
dependencies {
    runtimeOnly(files("libs/a.jar", "libs/b.jar"))
    runtimeOnly(fileTree("libs") { include("*.jar") })
}
// end::file-dependencies[]
