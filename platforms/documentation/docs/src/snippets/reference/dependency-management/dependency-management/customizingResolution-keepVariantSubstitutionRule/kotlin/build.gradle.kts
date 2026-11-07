plugins {
    id("java-library")
}

repositories {
    maven { url = uri(layout.settingsDirectory.dir("repo")) }
}

dependencies {
    implementation(platform("com.example:platform:1.0"))
    implementation("org.other:util")
    implementation(testFixtures("com.example:lib:1.0"))
}

// tag::keep_variant_substitution[]
configurations.runtimeClasspath {
    resolutionStrategy.dependencySubstitution {
        all {
            requested.let {
                if (it is ModuleComponentSelector && it.group == "com.example") {
                    useTarget(withModule(it, "com.example.branch", it.module, "1.0-branch"))
                }
            }
        }
    }
}
// end::keep_variant_substitution[]

tasks.register("showResolution") {
    val root = configurations.runtimeClasspath.flatMap { it.incoming.resolutionResult.rootComponent }
    doLast {
        root.get().dependencies.filterIsInstance<ResolvedDependencyResult>().forEach {
            println("${it.requested.displayName} -> ${it.selected.id.displayName} (${it.resolvedVariant.displayName})")
        }
    }
}
