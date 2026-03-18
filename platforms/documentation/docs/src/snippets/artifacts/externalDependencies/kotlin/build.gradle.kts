repositories {
    mavenCentral()
    google()
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
    // 1) Regular module dependency using string notation (JAR)
    runtimeOnly("org.springframework:spring-core:2.5",
        "org.springframework:spring-aop:2.5")

    // 2) Regular module dependency with a config block
    runtimeOnly("org.hibernate:hibernate:3.0.5") {
        isTransitive = true
    }

    // 3) Classifier + extension (:resources@zip) — fetches a ZIP artifact instead of a JAR
    runtimeOnly("net.sf.docbook:docbook-xsl:1.75.2:resources@zip")

    // 4) Extension only (@aar) — Android AAR instead of JAR
    implementation("com.google.android.material:material:1.11.0@aar")
}
// end::module-dependencies[]

// tag::file-dependencies[]
dependencies {
    runtimeOnly(files("libs/a.jar", "libs/b.jar"))
    runtimeOnly(fileTree("libs") { include("*.jar") })
}
// end::file-dependencies[]
