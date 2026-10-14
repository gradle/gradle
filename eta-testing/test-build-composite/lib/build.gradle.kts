// Tasks of the included build cannot depend on the intro task of the root build
listOf("libA", "libB").forEach { name ->
    tasks.register(name) { doLast { Thread.sleep(3000) } }
}
