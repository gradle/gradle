tasks.whenTaskAdded {
    extra["srcDir"] = "src/main/java"
}

val a = task("a")

println("source dir is ${a.extra["srcDir"]}")
