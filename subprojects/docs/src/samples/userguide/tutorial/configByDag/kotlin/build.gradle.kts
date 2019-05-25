tasks.register("distribution") {
    doLast {
        println("We build the zip with version=$version")
    }
}

tasks.register("release") {
    dependsOn("distribution")
    doLast {
        println("We release now")
    }
}

gradle.taskGraph.whenReady {
    version =
        if (hasTask(":release")) "1.0"
        else "1.0-SNAPSHOT"
}
