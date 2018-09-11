ant.lifecycleLogLevel = AntBuilder.AntMessagePriority.INFO

task("hello") {
    doLast {
        ant.withGroovyBuilder {
            "echo"("level" to "info", "message" to "hello from info priority!")
        }
    }
}
