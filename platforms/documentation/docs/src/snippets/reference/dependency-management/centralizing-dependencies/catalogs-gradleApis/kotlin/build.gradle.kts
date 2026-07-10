plugins {
    `java-library`
}

// tag::use_catalog_entries[]
dependencies {
    // Provider<MinimalExternalModuleDependency>
    implementation(libs.groovy.core)
    // MinimalExternalModuleDependency
    implementation(libs.groovy.core.get())
}
// end::use_catalog_entries[]

// tag::use_provider_in_apis[]
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        all {
            val componentSelector = requested
            if (componentSelector is ModuleComponentSelector
                && componentSelector.group == "org.codehaus.groovy"
                && componentSelector.module == "groovy-all") {
                useTarget(libs.groovy.core)
            }
        }
    }
}
// end::use_provider_in_apis[]
