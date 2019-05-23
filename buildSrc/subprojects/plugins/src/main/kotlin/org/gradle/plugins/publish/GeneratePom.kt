package org.gradle.plugins.publish

import accessors.base
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.plugins.MavenRepositoryHandlerConvention
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Upload
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.withConvention
import org.gradle.kotlin.dsl.withType
import org.gradle.kotlin.dsl.*
import java.io.File


open class GeneratePom : DefaultTask() {
    @OutputFile
    val pomFile = File(temporaryDir, "pom.xml")

    @get:Internal
    val publishImplementation by project.configurations.creating

    @get:Internal
    val publishRuntime by project.configurations.creating

    init {
        // Subprojects assign dependencies to publishCompile to indicate that they should be part of the published pom.
        // Therefore implementation needs to contain those dependencies and extend publishImplementation
        project.configurations.getByName("implementation") {
            extendsFrom(publishImplementation)
        }
        // Never up to date; we don't understand the data structures.
        outputs.upToDateWhen(Specs.satisfyNone<Task>())
    }

    @TaskAction
    fun generatePom(): Unit = project.run {
        addDependenciesToPublishConfigurations()
        val install by tasks.getting(Upload::class)
        install.repositories {
            withConvention(MavenRepositoryHandlerConvention::class) {
                mavenInstaller {
                    pom {
                        scopeMappings.mappings.clear()
                        scopeMappings.addMapping(300, publishRuntime, Conf2ScopeMappingContainer.RUNTIME)
                        groupId = project.group.toString()
                        artifactId = base.archivesBaseName
                        version = project.version.toString()
                        writeTo(pomFile)
                    }
                }
            }
        }
    }

    private
    fun Project.addDependenciesToPublishConfigurations() {
        dependencies {
            publishImplementation.allDependencies.withType<ProjectDependency>().forEach {
                publishRuntime("org.gradle:${it.dependencyProject.base.archivesBaseName}:$version")
            }
            publishImplementation.allDependencies.withType<ExternalDependency>().forEach {
                publishRuntime(it)
            }
        }
    }
}
