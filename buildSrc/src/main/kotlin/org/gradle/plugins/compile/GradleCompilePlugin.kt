package org.gradle.plugins.compile

import accessors.java
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType


open class GradleCompilePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val rootProject = project.rootProject
        if (rootProject == project) {
            val projectInternal = project as ProjectInternal
            val javaInstallationProbe = projectInternal.services.get(JavaInstallationProbe::class.java)
            val javaHomes = if (project.hasProperty("java7Home")) {
                listOf(project.property("java7Home"))
            } else {
                emptyList()
            }
            project.extensions.create("availableJdks", AvailableJdks::class.java, javaHomes, javaInstallationProbe)
        }

        val availableJdks = rootProject.the<AvailableJdks>()

        project.tasks.withType<JavaCompile> {
            options.isIncremental = true
            configureCompileTask(this, options, availableJdks)
        }
        project.tasks.withType<GroovyCompile> {
            configureCompileTask(this, options, availableJdks)
        }
    }

    private fun configureCompileTask(compileTask: AbstractCompile, options: CompileOptions, availableJdks: AvailableJdks) {
        options.isFork = true
        options.encoding = "utf-8"
        options.compilerArgs = mutableListOf("-Xlint:-options", "-Xlint:-path")
        val targetJdkVersion = maxOf(compileTask.project.java.targetCompatibility, JavaVersion.VERSION_1_7)
        val jdkForCompilation = availableJdks.jdkFor(targetJdkVersion)
        if (!jdkForCompilation.current) {
            options.forkOptions.javaHome = jdkForCompilation.javaHome
        }
        compileTask.inputs.property("javaInstallation", jdkForCompilation.displayName)
    }
}
