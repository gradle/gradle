plugins {
    java
}

// tag::bundle-task[]
task<JavaExec>("bundle") {
    val scripts = file("scripts")
    val bundle = file("$buildDir/bundle.js")

    inputs.dir(scripts)
    outputs.file(bundle)

    main = "org.gradle.sample.Bundle"
    classpath = project.sourceSets["main"].runtimeClasspath
    args(scripts.toString(), bundle.toString())
}
// end::bundle-task[]

task("printBundle") {
    dependsOn("bundle")
    doLast {
        println(file("$buildDir/bundle.js").readText())
    }
}
