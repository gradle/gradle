tasks.register("hello")
tasks.register<Copy>("copy")

println(tasks["hello"].name)
println(tasks.named("hello").get().name)

println(tasks.getByName<Copy>("copy").destinationDir)
println(tasks.named<Copy>("copy").get().destinationDir)
