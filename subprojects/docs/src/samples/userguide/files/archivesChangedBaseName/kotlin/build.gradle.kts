plugins {
    base
}

version = "1.0"
base.archivesBaseName = "gradle"

val myZip by tasks.registering(Zip::class) {
    from("somedir")
}

val myOtherZip by tasks.registering(Zip::class) {
    appendix = "wrapper"
    classifier = "src"
    from("somedir")
}

tasks.register("echoNames") {
    doLast {
        println("Project name: ${project.name}")
        println(myZip.get().archiveName)
        println(myOtherZip.get().archiveName)
    }
}
