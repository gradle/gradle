package org.gradle.kotlin.dsl.experiments.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskState

import org.gradle.cache.internal.GeneratedGradleJarCache
import org.gradle.internal.logging.ConsoleRenderer

import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf


private
val rulesetChecksum by lazy {
    GradleKotlinDslKtlintConventionPlugin::class.java.getResource("gradle-kotlin-dsl-ruleset.md5").readText()
}


private
val rulesetJar by lazy {
    GradleKotlinDslKtlintConventionPlugin::class.java.getResource("gradle-kotlin-dsl-ruleset.jar")
}


open class GradleKotlinDslKtlintConventionPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        plugins.apply("org.jlleitschuh.gradle.ktlint")

        configure<KtlintExtension> {
            version = DefaultVersions.ktlint
            reporters = arrayOf(ReporterType.PLAIN)
        }

        val ktlint by configurations.creating {
            exclude(module = "ktlint-ruleset-standard")
        }

        dependencies {
            ktlint(files(gradleKotlinDslKtlintRulesetJar()))
        }

        plugins.withId("kotlin") {
            afterEvaluate {
                fixKtlintTasks()
            }
        }
    }


    private
    fun Project.gradleKotlinDslKtlintRulesetJar() = provider {
        serviceOf<GeneratedGradleJarCache>().get("ktlint-convention-ruleset-$rulesetChecksum") { jar ->
            jar.outputStream().use { it.write(rulesetJar.readBytes()) }
        }
    }


    // Note that below are workarounds, not how things should be fixed upstream
    // https://github.com/JLLeitschuh/ktlint-gradle/issues/67
    // https://github.com/JLLeitschuh/ktlint-gradle/issues/51
    private
    fun Project.fixKtlintTasks() {
        val reporters = the<KtlintExtension>().reporters
        val ktLintCheckTasks = collectKtLintCheckTasks()
        fixKtlintCheckTaskCacheability(ktLintCheckTasks, reporters)
        displayLinkToReportsOnFailure(ktLintCheckTasks, reporters)
    }


    private
    fun Project.collectKtLintCheckTasks() =
        the<JavaPluginConvention>().sourceSets.mapNotNull { sourceSet ->
            (tasks.findByName("ktlint${sourceSet.name.capitalize()}Check") as? JavaExec)?.let { task ->
                Pair(sourceSet, task)
            }
        }


    private
    fun Project.fixKtlintCheckTaskCacheability(
        tasksBySourceSets: List<Pair<SourceSet, JavaExec>>,
        reporters: Array<ReporterType>
    ) =

        tasksBySourceSets.forEach { (sourceSet, task) ->

            sourceSet.allSource.sourceDirectories.forEach { srcDir ->
                task.inputs.property(
                    "kt files from $srcDir",
                    files(srcDir).asFileTree.matching {
                        it.include("**/*.kt")
                    }
                )
                reporters.forEach {
                    task.outputs.file("$buildDir/${it.reportPathFor(sourceSet)}")
                }
                task.outputs.cacheIf { true }
            }
        }


    private
    fun Project.displayLinkToReportsOnFailure(
        tasksBySourceSets: List<Pair<SourceSet, JavaExec>>,
        reporters: Array<ReporterType>
    ) =

        gradle.taskGraph.addTaskExecutionListener(object : TaskExecutionListener {

            val consoleRenderer = ConsoleRenderer()

            override fun beforeExecute(aTask: Task) = Unit

            override fun afterExecute(aTask: Task, state: TaskState) {
                if (state.failure != null) {
                    tasksBySourceSets.find { it.second == aTask }?.let { (sourceSet, _) ->
                        val message = "ktlint check failed\n\n" + reporters.map {
                            file("$buildDir/${it.reportPathFor(sourceSet)}")
                        }.joinToString(separator = "\n") {
                            consoleRenderer.asClickableFileUrl(it).prependIndent()
                        } + '\n'
                        logger.error(message)
                    }
                }
            }
        })

    private
    fun ReporterType.reportPathFor(sourceSet: SourceSet) =
        "reports/ktlint/ktlint-${sourceSet.name}.$fileExtension"
}
