import org.gradle.api.tasks.GradleBuild

fun isProjectDir(candidate: File) =
    candidate.isDirectory && File(candidate, "settings.gradle").exists()

val subProjectTasks = rootDir.listFiles().filter { isProjectDir(it) }.map { subProjectDir ->
    task<GradleBuild>("prepare-${subProjectDir.name}") {
        setDir(subProjectDir)
    }
}

setDefaultTasks(subProjectTasks.map { it.name })
