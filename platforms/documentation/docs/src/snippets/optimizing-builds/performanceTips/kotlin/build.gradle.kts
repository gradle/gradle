import java.net.URL

plugins {
    id("java")
    id("com.example.my-custom-plugin") apply false
}

repositories {
    mavenCentral()
}

// tag::expensive-plugin[]
class ExpensivePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // BAD: Makes an expensive network call at configuration time
        val response = URL("https://example.com/dependencies.json").readText()
        val dependencies = groovy.json.JsonSlurper().parseText(response) as List<*>

        dependencies.forEach { dep ->
            project.dependencies.add("implementation", dep!!)
        }
    }
}
// end::expensive-plugin[]

// tag::optimized-plugin[]
class OptimizedPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("fetchDependencies") {
            doLast {
                // GOOD: Runs only when the task is executed
                val response = URL("https://example.com/dependencies.json").readText()
                val dependencies = groovy.json.JsonSlurper().parseText(response) as List<*>

                dependencies.forEach { dep ->
                    project.dependencies.add("implementation", dep!!)
                }
            }
        }
    }
}
// end::optimized-plugin[]

// tag::apply-selectively[]
project(":subproject1") {
    apply(from = "$rootDir/script-a.gradle")  // Applied only where needed
}

project(":subproject2") {
    apply(from = "$rootDir/script-a.gradle")
}
// end::apply-selectively[]

// tag::apply-custom-plugin[]
project(":subproject1") {
    apply(plugin = "com.example.my-custom-plugin")  // Apply only where needed
}

project(":subproject2") {
    apply(plugin = "com.example.my-custom-plugin")
}
// end::apply-custom-plugin[]

// tag::cache-dynamic-versions[]
configurations.all {
    resolutionStrategy {
        cacheDynamicVersionsFor(4, "hours")
        cacheChangingModulesFor(10, "minutes")
    }
}
// end::cache-dynamic-versions[]

// tag::defer-resolution[]
tasks.register("printDeps") {
    doFirst {
        configurations.getByName("compileClasspath").files.forEach { println(it) } // Deferring Dependency Resolution
    }
    doLast {
        configurations.getByName("compileClasspath").files.forEach { println(it) } // Resolving Dependencies During Configuration
    }
}
// end::defer-resolution[]

// tag::custom-resolution-logic[]
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.example" && requested.name == "library") {
            val versionInfo = URL("https://example.com/version-check").readText()  // Remote call during resolution
            useVersion(versionInfo.trim())  // Dynamically setting a version based on an HTTP response
        }
    }
}
// end::custom-resolution-logic[]
