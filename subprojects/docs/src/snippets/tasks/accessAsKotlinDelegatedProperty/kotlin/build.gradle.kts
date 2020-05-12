task("hello")
task<Copy>("copy")

// Access tasks using Kotlin delegated properties

val hello by tasks.getting
println(hello.name)

val copy by tasks.getting(Copy::class)
println(copy.destinationDir)
