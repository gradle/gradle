/*
 * This sample demonstrates the ability to selectively include projects
 * from the local directory rather than using an external dependency.
 *
 * By default all projects are considered external and are picked up
 * from the "repo" ivy repository.  To include local projects in a build,
 * set the "useLocal" system property on the gradle command line:
 *
 *   gradle -DuseLocal=project1,project2 :showJarFiles
 *
 */

allprojects {
    apply(plugin = "java-library")

    group = "org.example"
    version = "1.0"

    val repoUrl = "file://${rootProject.projectDir}/repo"

    repositories {
        ivy { url = uri(repoUrl) }
    }

    // tag::project_substitution[]
    configurations.all {
        resolutionStrategy.dependencySubstitution.all {
            requested.let {
                if (it is ModuleComponentSelector && it.group == "org.example") {
                    val targetProject = findProject(":${it.module}")
                    if (targetProject != null) {
                        useTarget(targetProject)
                    }
                }
            }
        }
    }
    // end::project_substitution[]
}

dependencies {
    "implementation"("org.example:project1:1.0")
}

tasks.register("showJarFiles") {
    doLast {
        configurations["compileClasspath"].forEach { println(it.path.removePrefix(rootDir.path)) }
    }
}
