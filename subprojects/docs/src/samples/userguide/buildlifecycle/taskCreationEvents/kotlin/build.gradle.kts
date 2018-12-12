tasks.whenTaskAdded {
    extra["srcDir"] = "src/main/java"
}

val a by tasks.registering

println("source dir is ${a.get().extra["srcDir"]}")
