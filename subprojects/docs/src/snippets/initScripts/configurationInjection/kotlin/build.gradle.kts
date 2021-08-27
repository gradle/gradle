repositories {
    mavenCentral()
}

tasks.register("showRepos") {
    doLast {
        println("All repos:")
        println(repositories.map { it.name })
    }
}
