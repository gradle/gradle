// to be called immediately before a project is evaluated.
gradle.lifecycle.beforeProject {
    println("[lifecycle.beforeProject] Started configuring ${path}")
}

// 4. beforeProject: runs before each build.gradle(.kts) is evaluated
// to be called immediately before a project is evaluated.
gradle.beforeProject {
    println("[beforeProject] Started configuring ${path}")

    println("[beforeProject] Setup a global build directory for ${name}")
    layout.buildDirectory.set(
        layout.projectDirectory.dir("build")
    )
}
