import org.gradle.api.tasks.GradleBuild

fun isProjectDir(candidate: File) =
    File(candidate, "settings.gradle.kts").isFile

fun isCompatibleWithJDK(candidate: File) =
    candidate.name.endsWith("kotlin-1.1") && JavaVersion.current() < JavaVersion.VERSION_1_9

val subProjectTasks = rootDir
    .listFiles()
    .filter { isProjectDir(it) }
    .filter { isCompatibleWithJDK(it) }
    .map { subProjectDir ->
        task<GradleBuild>("prepare-${subProjectDir.name}") {
            dir = subProjectDir
        }
    }

defaultTasks = subProjectTasks.map { it.name }
