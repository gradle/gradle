val compile = configurations.create("compile")

// tag::generated-file-dependencies[]
dependencies {
    compile(files("$buildDir/classes") {
        builtBy("compile")
    })
}

tasks.register("compile") {
    doLast {
        println("compiling classes")
    }
}

tasks.register("list") {
    dependsOn(configurations["compile"])
    doLast {
        println("classpath = ${configurations["compile"].map { file: File -> file.name }}")
    }
}
// end::generated-file-dependencies[]
