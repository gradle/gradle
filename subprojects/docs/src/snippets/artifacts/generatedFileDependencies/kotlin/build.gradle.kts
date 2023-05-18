val implementation = configurations.create("implementation")
val compileClasspath = configurations.create("compileClasspath") {
    extendsFrom(implementation)
}

// tag::generated-file-dependencies[]
dependencies {
    implementation(files(layout.buildDirectory.dir("classes")) {
        builtBy("compile")
    })
}

tasks.register("compile") {
    doLast {
        println("compiling classes")
    }
}

tasks.register("list") {
    val compileClasspath: FileCollection = configurations["compileClasspath"]
    dependsOn(compileClasspath)
    doLast {
        println("classpath = ${compileClasspath.map { file: File -> file.name }}")
    }
}
// end::generated-file-dependencies[]
