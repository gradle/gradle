project(":projectA") {
    tasks.register("hello")
}

tasks.register("hello")

println(tasks.getByPath("hello").path)
println(tasks.getByPath(":hello").path)
println(tasks.getByPath("projectA:hello").path)
println(tasks.getByPath(":projectA:hello").path)
