ant.importBuild("../common/build.xml")

tasks.named("hello") {
    doLast {
        println("Hello, from Gradle")
    }
}
