plugins {
    java
}

// tag::bundle-task[]
task<JavaExec>("bundle") {
    val scripts = file("scripts")
    val bundle = file("$buildDir/bundle.js")

    outputs.cacheIf { true }

    inputs.dir(scripts)
        .withPropertyName("scripts")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(bundle)
        .withPropertyName("bundle")

    main = "org.gradle.sample.Bundle"
    classpath = project.sourceSets["main"].runtimeClasspath
    args(scripts.toRelativeString(projectDir), bundle.toRelativeString(projectDir))
}
// end::bundle-task[]

task("printBundle") {
    dependsOn("bundle")
    doLast {
        println(file("$buildDir/bundle.js").readText())
    }
}
