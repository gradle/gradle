plugins {
    base
}

version = "1.0"
base.archivesBaseName = "gradle"

val myZip by tasks.creating(Zip::class) {
    from("somedir")
}

val myOtherZip by tasks.creating(Zip::class) {
    appendix = "wrapper"
    classifier = "src"
    from("somedir")
}

task("echoNames") {
    doLast {
        println("Project name: ${project.name}")
        println(myZip.archiveName)
        println(myOtherZip.archiveName)
    }
}
