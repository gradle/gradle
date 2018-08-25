val compile by configurations.creating

// tag::generated-file-dependencies[]
dependencies {
    compile(files("$buildDir/classes") {
        builtBy("compile")
    })
}

tasks {
    create("compile") {
        doLast {
            println("compiling classes")
        }
    }

    create("list") {
        dependsOn(configurations["compile"])
        doLast {
            println("classpath = ${configurations["compile"].map { file: File -> file.name }}")
        }
    }
}
// end::generated-file-dependencies[]
