val intro = tasks.register("intro") {
    doLast {
        println("4 app tasks (2s each) depend on 2 tasks of the included build lib (3s each).")
        println("Shows how the ETA covers work in an included build that the root build depends on.")
    }
}

(1..4).forEach { i ->
    tasks.register("app$i") {
        dependsOn(intro)
        dependsOn(gradle.includedBuild("lib").task(":libA"), gradle.includedBuild("lib").task(":libB"))
        doLast { Thread.sleep(2000) }
    }
}
tasks.register("all") { dependsOn(intro, (1..4).map { "app$it" }) }
