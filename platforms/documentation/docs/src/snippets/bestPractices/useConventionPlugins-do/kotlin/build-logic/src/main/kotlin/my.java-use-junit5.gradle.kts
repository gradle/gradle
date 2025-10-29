// tag::do-this[]
plugins {
    `java-library`
}

tasks.withType<Test>().configureEach { // <3>
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
}
// end::do-this[]

val conventionsApplied = tasks.register("myJavaUseJunit5Applied")
tasks.compileJava.configure { dependsOn(conventionsApplied) }
