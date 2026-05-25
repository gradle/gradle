// Setting a provider
val simpleMessageProvider: Provider<String> = project.providers.provider { "Hello, World from a Provider!" }
// Accessing a provider
println(simpleMessageProvider.get())
