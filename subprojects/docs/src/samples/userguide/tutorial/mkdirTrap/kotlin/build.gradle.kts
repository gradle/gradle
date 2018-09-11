val classesDir = file("build/classes")
classesDir.mkdirs()
task<Delete>("clean") {
    delete("build")
}
task("compile") {
    dependsOn("clean")
    doLast {
        if (!classesDir.isDirectory) {
            println("The class directory does not exist. I can not operate")
            // do something
        }
        // do something
    }
}
