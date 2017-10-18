import org.gradle.api.tasks.GradleBuild

fun isProjectDir(candidate: File) =
    candidate.isDirectory && File(candidate, "settings.gradle").exists()

fun isCompatibleWithJDK(candidate: File) =
    candidate.name.endsWith("kotlin-1.0") && JavaVersion.current() < JavaVersion.VERSION_1_9

val subProjectTasks = rootDir
    .listFiles()
    .filter { isProjectDir(it) }
    .filter { isCompatibleWithJDK(it) }
    .map { subProjectDir ->
        task<GradleBuild>("prepare-${subProjectDir.name}") {
            setDir(subProjectDir)
        }
    }

setDefaultTasks(subProjectTasks.map { it.name })
