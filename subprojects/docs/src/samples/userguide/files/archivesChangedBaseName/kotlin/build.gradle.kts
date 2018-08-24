plugins {
    base
}

version = "1.0"
project.setProperty("archivesBaseName", "gradle")

val myZip by tasks.creating(Zip::class) {
    from("somedir")
}

val myOtherZip by tasks.creating(Zip::class) {
    appendix = "wrapper"
    classifier = "src"
    from("somedir")
}

tasks.create("echoNames") {
    doLast {
        println("Project name: ${project.name}")
        println(myZip.archiveName)
        println(myOtherZip.archiveName)
    }
}
