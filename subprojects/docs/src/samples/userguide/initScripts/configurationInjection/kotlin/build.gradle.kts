repositories {
    mavenCentral()
}

task("showRepos") {
    doLast {
        println("All repos:")
        println(repositories.map { it.name })
    }
}
