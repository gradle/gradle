val implementation = configurations.create("implementation")
val compileClasspath = configurations.create("compileClasspath") {
    extendsFrom(implementation)
}

// tag::generated-file-dependencies[]
dependencies {
    implementation(files("$buildDir/classes") {
        builtBy("compile")
    })
}

tasks.register("compile") {
    doLast {
        println("compiling classes")
    }
}

tasks.register("list") {
    dependsOn(configurations["compileClasspath"])
    doLast {
        println("classpath = ${configurations["compileClasspath"].map { file: File -> file.name }}")
    }
}
// end::generated-file-dependencies[]
