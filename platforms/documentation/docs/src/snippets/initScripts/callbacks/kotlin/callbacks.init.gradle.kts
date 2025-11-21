import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

println("[INIT] Gradle ${gradle.gradleVersion}")
println("")

// tag::beforesettings[]
// 1. beforeSettings: tweak start parameters / log early info
// before the build settings have been loaded and evaluated.
gradle.beforeSettings {
    println("[beforeSettings] gradleUserHome = ${gradle.gradleUserHomeDir}")

    // Example: default to --parallel if an env var is set
    if (System.getenv("CI") == "true") {
        println("[beforeSettings] Enabling parallel execution on CI")
        gradle.startParameter.isParallelProjectExecutionEnabled = true
    } else {
        println("[beforeSettings] Disabling parallel execution, not on CI")
        gradle.startParameter.isParallelProjectExecutionEnabled = false
    }
    // end::beforesettings[]
    println("")
    // tag::beforesettings[]
}
// end::beforesettings[]

// tag::settingsevaluated[]
// 2. settingsEvaluated: adjust build layout / repositories / scan config
// when the build settings have been loaded and evaluated.
gradle.settingsEvaluated {
    println("[settingsEvaluated] rootProject = ${rootProject.name}")

    // Example: enforce a company-wide pluginManagement repo
    pluginManagement.repositories.apply {
        println("[settingsEvaluated] Ensuring company plugin repo is configured")
        mavenCentral()
    }
    // end::settingsevaluated[]
    println("")
    // tag::settingsevaluated[]
}
// end::settingsevaluated[]

// tag::projectsloaded[]
// 3. projectsLoaded: we know the full project graph, but nothing configured yet
// to be called when the projects for the build have been created from the settings.
gradle.projectsLoaded {
    println("[projectsLoaded] Projects discovered: " + rootProject.allprojects.joinToString { it.name })

    // Example: Add a custom property (using the extra properties extension)
    allprojects {
        println("[projectsLoaded] Setting extra property on ${name}")
        extensions.extraProperties["isInitScriptConfigured"] = true
    }
}
// end::projectsloaded[]

// tag::beforeproject[]
// to be called immediately before a project is evaluated.
gradle.lifecycle.beforeProject {
    println("[lifecycle.beforeProject] Started configuring ${path}")
}

// 4. beforeProject: runs before each build.gradle(.kts) is evaluated
// to be called immediately before a project is evaluated.
gradle.beforeProject {
    println("[beforeProject] Started configuring ${path}")

    println("[beforeProject] Setup a global build directory for ${name}")
    layout.buildDirectory.set(
        layout.projectDirectory.dir("build")
    )
}
// end::beforeproject[]

// tag::afterproject[]
// 5. afterProject: runs after each build.gradle(.kts) is evaluated
// to be called immediately after a project is evaluated.
gradle.afterProject {
    println("[afterProject] Finished configuring ${path}")

    // Example: apply the Java plugin to all projects that don’t have any plugin yet
    if (plugins.hasPlugin("java")) {
        println("[afterProject] ${path} already has the java plugin")
    } else {
        println("[afterProject] Applying java plugin to ${path}")
        apply(plugin = "java")
    }
}

// to be called immediately after a project is evaluated.
gradle.lifecycle.afterProject {
    println("[lifecycle.afterProject] Finished configuring ${path}")
}
// end::afterproject[]

// tag::projectsevaluated[]
// 6. projectsEvaluated: all projects are fully configured, safe for cross-project checks
// to be called when all projects for the build have been evaluated.
gradle.projectsEvaluated {
    // end::projectsevaluated[]
    println("")
    // tag::projectsevaluated[]
    println("[projectsEvaluated] All projects evaluated")

    // Example: globally configure the java plugin
    allprojects {
        extensions.findByType<JavaPluginExtension>()?.let { javaExtension ->
            if (javaExtension.toolchain.languageVersion.isPresent) {
                println("[projectsEvaluated] ${path} uses Java plugin with toolchain ${javaExtension.toolchain.displayName}")
            } else {
                println("[projectsEvaluated] WARNING: ${path} uses Java plugin but no toolchain is configured, setting Java 17")
                javaExtension.toolchain.languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
    }
}
// end::projectsevaluated[]
