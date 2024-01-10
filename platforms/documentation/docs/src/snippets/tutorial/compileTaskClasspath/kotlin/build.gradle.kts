plugins {
    groovy
}
dependencies {
    implementation(localGroovy())
}

// tag::compile-task-classpath[]
tasks.named<AbstractCompile>("compileGroovy") {
    // Groovy only needs the declared dependencies
    // (and not longer the output of compileJava)
    classpath = sourceSets.main.get().compileClasspath
}
tasks.named<AbstractCompile>("compileJava") {
    // Java also depends on the result of Groovy compilation
    // (which automatically makes it depend of compileGroovy)
    classpath += files(sourceSets.main.get().groovy.classesDirectory)
}
// end::compile-task-classpath[]
