// tag::do-this[]
plugins {
    `java-library`
}
// <1>
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}
// end::do-this[]

val conventionsApplied = tasks.register("myBaseJavaLibraryApplied")
tasks.compileJava.configure { dependsOn(conventionsApplied) }
