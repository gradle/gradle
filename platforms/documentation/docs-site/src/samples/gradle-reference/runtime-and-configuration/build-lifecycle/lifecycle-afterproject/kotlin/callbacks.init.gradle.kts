// 5. afterProject: runs after each build.gradle(.kts) is evaluated
// to be called immediately after a project is evaluated.
gradle.afterProject {
    println("[afterProject] Finished configuring ${path}")

    // Example: apply the Java plugin to all projects that don't have any plugin yet
    if (plugins.hasPlugin("java")) {
        println("[afterProject] ${path} already has the java plugin")
    } else {
        println("[afterProject] Applying java plugin to ${path}")
        apply(plugin = "java")
    }
}

// to be called immediately after a project is evaluated.
gradle.lifecycle.afterProject {
    println("[lifecycle.afterProject] Finished configuring ${path}")
}
