rootProject.name = "component-metadata-rules"

// tag::rule-in-settings[]
dependencyResolutionManagement {
    components {
        withModule<GuavaRule>("com.google.guava:guava")
    }
}
// end::rule-in-settings[]

// tag::prefer-settings[]
dependencyResolutionManagement {
    rulesMode = RulesMode.PREFER_SETTINGS
}
// end::prefer-settings[]

// tag::enforce-settings[]
dependencyResolutionManagement {
    rulesMode = RulesMode.FAIL_ON_PROJECT_RULES
}
// end::enforce-settings[]

// tag::prefer-projects[]
dependencyResolutionManagement {
    rulesMode = RulesMode.PREFER_PROJECT
}
// end::prefer-projects[]

@CacheableRule
abstract class GuavaRule: ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        val variantVersion = context.details.id.version
        val version = variantVersion.substring(0, variantVersion.indexOf("-"))
        listOf("compile", "runtime").forEach { base ->
            mapOf(6 to "android", 8 to "jre").forEach { (targetJvmVersion, jarName) ->
                context.details.addVariant("jdk$targetJvmVersion${base.capitalize()}", base) {
                    attributes {
                        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetJvmVersion)
                    }
                    withFiles {
                        removeAllFiles()
                        addFile("guava-$version-$jarName.jar", "../$version-$jarName/guava-$version-$jarName.jar")
                    }
                }
            }
        }
    }
}
