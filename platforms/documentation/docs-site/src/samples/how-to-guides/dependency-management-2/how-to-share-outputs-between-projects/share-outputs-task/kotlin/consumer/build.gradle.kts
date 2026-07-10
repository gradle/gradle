tasks.register<JavaExec>("runWithInstrumentation") {
    // Use the resolved instrumented classpath
    classpath = instrumentedRuntime
    mainClass.set("com.example.Main")
}
