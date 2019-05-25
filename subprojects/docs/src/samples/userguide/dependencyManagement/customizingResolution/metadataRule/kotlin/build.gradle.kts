repositories {
    ivy {
        url = uri("$projectDir/repo")
    }
}

configurations {
    create("config1")
    create("config2")
    create("config3")
    create("config4")
    create("config5")
    create("config6")
}

// tag::latest-selector[]
dependencies {
    "config1"("org.sample:client:latest.integration")
    "config2"("org.sample:client:latest.release")
}

tasks.register("listConfigs") {
    doLast {
        configurations["config1"].forEach { println(it.name) }
        println()
        configurations["config2"].forEach { println(it.name) }
    }
}
// end::latest-selector[]

// tag::custom-status-scheme[]
class CustomStatusRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        val details = context.details
        if (details.id.group == "org.sample" && details.id.name == "api") {
            details.statusScheme = listOf("bronze", "silver", "gold", "platinum")
        }
    }
}

dependencies {
    "config3"("org.sample:api:latest.silver")
    components {
        all(CustomStatusRule::class.java)
    }
}
// end::custom-status-scheme[]

tasks.register("listApi") {
    doLast {
        configurations["config3"].forEach { println("Resolved: ${it.name}") }
    }
}

// tag::custom-status-scheme-module[]
class ModuleStatusRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.statusScheme = listOf("int", "rc", "prod")
    }
}

dependencies {
    "config4"("org.sample:lib:latest.prod")
    components {
        withModule("org.sample:lib", ModuleStatusRule::class.java)
    }
}
// end::custom-status-scheme-module[]

tasks.register("listLib") {
    doLast {
        configurations["config4"].forEach { println("Resolved: ${it.name}") }
    }
}

// tag::ivy-component-metadata-rule[]
class IvyComponentRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        val descriptor = context.getDescriptor(IvyModuleDescriptor::class)
        if (descriptor != null && descriptor.branch == "testing") {
            context.details.status = "rc"
        }
    }
}
dependencies {
    "config5"("org.sample:lib:latest.rc")
    components {
        withModule("org.sample:lib", IvyComponentRule::class.java)
    }
}
// end::ivy-component-metadata-rule[]

tasks.register("listWithIvyRule") {
    doLast {
        configurations["config5"].forEach { println("Resolved: ${it.name}") }
    }
}

// tag::config-component-metadata-rule[]
class ConfiguredRule @javax.inject.Inject constructor(val param: String) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        if (param == "sampleValue") {
            context.details.statusScheme = listOf("bronze", "silver", "gold", "platinum")
        }
    }
}
dependencies {
    "config6"("org.sample:api:latest.gold")
    components {
        withModule("org.sample:api", ConfiguredRule::class.java) {
            params("sampleValue")
        }
    }
}

// end::config-component-metadata-rule[]

tasks.register("listWithConfiguredRule") {
    doLast {
        configurations["config6"].forEach { println("Resolved: ${it.name}") }
    }
}
