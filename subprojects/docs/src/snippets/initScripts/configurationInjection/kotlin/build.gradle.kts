repositories {
    mavenCentral()
}

tasks.register("showRepos") {
    doLast {
        println("All repos:")
        //TODO:kotlin-dsl remove filter once we're no longer on a kotlin eap
        println(repositories.map { it.name }.filter { it != "maven" })
    }
}
