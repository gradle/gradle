task("hello")
task<Copy>("copy")

println(tasks["hello"].name)
println(tasks.getByName("hello").name)

println(tasks.getByName<Copy>("copy").destinationDir)
