import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.testing.IntegrationTest

val sourceSets = the<JavaPluginConvention>().sourceSets

val integTest by sourceSets.creating {
    val main by sourceSets
    compileClasspath += main.output
    runtimeClasspath += main.output
}

configurations {
    "integTestCompile" { extendsFrom(configurations["testCompile"]) }
    "integTestRuntime" { extendsFrom(configurations["testRuntime"]) }
    "integTestImplementation" { extendsFrom(configurations["testImplementation"]) }
    "partialDistribution" { extendsFrom(configurations["integTestRuntimeClasspath"]) }
}

val integTestCompile by configurations
val integTestRuntime by configurations

dependencies {
    integTestCompile(project(":internalIntegTesting"))

    //so that implicit help tasks are available:
    integTestRuntime(project(":diagnostics"))

    //So that the wrapper and init task are added when integTests are run via commandline
    integTestRuntime(project(":buildInit"))
    //above can be removed when we implement the auto-apply plugins
}

val integTestTasks by extra { tasks.withType<IntegrationTest>() }

integTestTasks.all {
    group = "verification"
    testClassesDirs = integTest.output.classesDirs
    classpath = integTest.runtimeClasspath
}

tasks {
    "integTest"(IntegrationTest::class) {
        val defaultExecuter = project.findProperty("defaultIntegTestExecuter") as? String ?: "embedded"
        description = "Runs integTests with '$defaultExecuter' executer"
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
    "check" { dependsOn("integTest") }
    listOf("embedded", "forking", "noDaemon", "parallel").forEach { mode ->
        "${mode}IntegTest"(IntegrationTest::class) {
            description = "Runs integTests with '$mode' executer"
            systemProperties["org.gradle.integtest.executer"] = mode
        }
    }
}

// lazy as plugin not applied yet
plugins.withType<IdeaPlugin> {
    configure<IdeaModel> {
        module {
            integTest.withConvention(GroovySourceSet::class) {
                testSourceDirs = testSourceDirs + groovy.srcDirs + integTest.resources.srcDirs
            }
            scopes["TEST"]!!["plus"]!!.apply {
                add(integTestCompile)
                add(integTestRuntime)
            }
        }
    }
}

// lazy as plugin not applied yet
plugins.withType<EclipsePlugin> {
    configure<EclipseModel> {
        classpath.plusConfigurations.apply {
            add(integTestCompile)
            add(integTestRuntime)
        }
    }
}
