repositories {
    mavenCentral()
}

val implementation by configurations.creating
val runtime by configurations.creating

// tag::define-dependency[]
dependencies {
    implementation("org.hibernate:hibernate-core:3.6.7.Final")
}
// end::define-dependency[]

// tag::use-configuration[]
tasks.create("listJars") {
    doLast {
        configurations["compile"].forEach { file: File -> println(file.name) }
    }
}
// end::use-configuration[]

// tag::module-dependencies[]
dependencies {
    runtime(group = "org.springframework", name = "spring-core", version = "2.5")
    runtime("org.springframework:spring-aop:2.5")
    runtime("org.hibernate:hibernate:3.0.5") {
        setTransitive(true)
    }
    runtime(group = "org.hibernate", name = "hibernate", version = "3.0.5") {
        setTransitive(true)
    }
}
// end::module-dependencies[]

// tag::file-dependencies[]
dependencies {
    runtime(files("libs/a.jar", "libs/b.jar"))
    runtime(fileTree("dir" to "libs", "include" to "*.jar"))
}
// end::file-dependencies[]
