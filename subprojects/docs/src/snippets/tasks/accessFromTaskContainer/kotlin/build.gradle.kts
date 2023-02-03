tasks.register("hello")
tasks.register<Copy>("copy")

println(tasks.named("hello").get().name) // or just 'tasks.hello' if the task was added by a plugin

println(tasks.named<Copy>("copy").get().destinationDir)
