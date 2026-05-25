// Setting a property
val simpleMessageProperty: Property<String> = project.objects.property(String::class)
simpleMessageProperty.set("Hello, World from a Property!")
// Accessing a property
println(simpleMessageProperty.get())
