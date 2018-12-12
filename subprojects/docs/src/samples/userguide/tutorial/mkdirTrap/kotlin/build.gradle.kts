val classesDir = file("build/classes")
classesDir.mkdirs()
tasks.register<Delete>("clean") {
    delete("build")
}
tasks.register("compile") {
    dependsOn("clean")
    doLast {
        if (!classesDir.isDirectory) {
            println("The class directory does not exist. I can not operate")
            // do something
        }
        // do something
    }
}
