package org.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import accessors.*
import org.gradle.plugins.testfixtures.TestFixturesExtension
import org.gradle.testing.CrossVersionTest

open class CrossVersionTestPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        apply { plugin("java") }
        val mainSourceSet by java.sourceSets.getting
        val crossVersionTest by java.sourceSets.creating {
            compileClasspath += mainSourceSet.output
            runtimeClasspath += mainSourceSet.output
        }

        val crossVersionTestImplementation by configurations.creating {
            extendsFrom(configurations.getByName("testImplementation"))
        }

        val crossVersionTestRuntime by configurations.creating {
            extendsFrom(configurations.getByName("testRuntime"))
        }

        val crossVersionTestRuntimeOnly by configurations.creating {
            extendsFrom(configurations.getByName("testRuntimeOnly"))
        }

        val partialDistribution by configurations.creating {
            extendsFrom(configurations.getByName(crossVersionTest.runtimeClasspathConfigurationName))
        }

        dependencies {
            configurations.getByName(crossVersionTest.compileConfigurationName) {
                project(":internalIntegTesting")
            }
            crossVersionTestRuntime(project(":diagnostics"))
            crossVersionTestRuntime(project(":buildInit"))
            crossVersionTestRuntime(project(":toolingApiBuilders"))
        }

        // Why can't I access the testFixtures accessor?
        the<TestFixturesExtension>().from(":toolingApi")
        val crossVersionTestTasks = tasks.withType(CrossVersionTest::class.java)
        extra.set("crossVersionTestTasks", crossVersionTestTasks)
        crossVersionTestTasks.all {
            group = "verification"
            testClassesDirs = crossVersionTest.output.classesDirs
            classpath = crossVersionTest.runtimeClasspath
            requiresLibsRepo = true
        }

        // I use the tasks.create syntaxt as the crossVersionTest name is already taken by the source set
        val crossVersionTestTask = tasks.create("crossVersionTest", CrossVersionTest::class.java) {
            val defaultIntegTestExecuter = project.property("defaultIntegTestExecuter")
            val defaultExecuter = if (defaultIntegTestExecuter != null) defaultIntegTestExecuter else "embedded"
            description = "Runs crossVersionTest with '${defaultExecuter}' executer"
            systemProperties["org.gradle.integtest.executer"] = defaultExecuter
            if (project.hasProperty("org.gradle.integtest.debug")) {
                systemProperties["org.gradle.integtest.debug"] = "true"
                testLogging.showStandardStreams = true
            }
            if (project.hasProperty("org.gradle.integtest.verbose")) {
                testLogging.showStandardStreams = true
            }
            if (project.hasProperty("org.gradle.integtest.launcher.debug")) {
                systemProperties["org.gradle.integtest.launcher.debug"] = "true"
            }

        }

        tasks.get("check").dependsOn(crossVersionTestTask)

        listOf("embedded", "forking").forEach { mode ->
            val taskName = "${mode}CrossVersionTest"
            tasks.create(taskName, CrossVersionTest::class.java) {
                description = "Runs crossVersionTests with '${mode}' executer"
                systemProperties["org.gradle.integtest.executer"] = mode
            }
        }

        val allVersionsCrossVersionTests by tasks.creating {
            description = "Runs the cross-version tests against all Gradle versions with 'forking' executer"
        }

        val quickFeedbackCrossVersionTests by tasks.creating {
            description = "Runs the cross-version tests against a subset of selected Gradle versions with 'forking' executer for quick feedback"
        }


    }


}

//releasedVersions.testedVersions.each { targetVersion ->
//    tasks.create("gradle${targetVersion}CrossVersionTest", CrossVersionTest).configure {
//        allVersionsCrossVersionTests.dependsOn it
//            description "Runs the cross-version tests against Gradle ${targetVersion}"
//        systemProperties['org.gradle.integtest.versions'] = targetVersion
//        systemProperties['org.gradle.integtest.executer'] = 'forking'
//    }
//}
//
//releasedVersions.getTestedVersions(true).each { targetVersion ->
//    tasks.getByName("gradle${targetVersion}CrossVersionTest").configure {
//        quickFeedbackCrossVersionTests.dependsOn it
//    }
//}
//
//plugins.withType(org.gradle.plugins.ide.idea.IdeaPlugin) { // lazy as plugin not applied yet
//    idea {
//        module {
//            testSourceDirs += sourceSets.crossVersionTest.groovy.srcDirs
//            testSourceDirs += sourceSets.crossVersionTest.resources.srcDirs
//            scopes.TEST.plus.add(configurations.crossVersionTestCompileClasspath)
//            scopes.TEST.plus.add(configurations.crossVersionTestRuntimeClasspath)
//        }
//    }
//}
//
//plugins.withType(org.gradle.plugins.ide.eclipse.EclipsePlugin) { // lazy as plugin not applied yet
//    eclipse {
//        classpath {
//            plusConfigurations.add(configurations.crossVersionTestCompileClasspath)
//            plusConfigurations.add(configurations.crossVersionTestRuntimeClasspath)
//        }
//    }
//}
