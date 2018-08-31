ant.importBuild("build.xml")

tasks.getByName("hello") {
    doLast {
        println("Hello, from Gradle")
    }
}
