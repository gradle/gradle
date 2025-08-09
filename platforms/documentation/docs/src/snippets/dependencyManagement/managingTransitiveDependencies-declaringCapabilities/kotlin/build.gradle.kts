plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::dependencies[]
dependencies {
    // This dependency will bring log4:log4j transitively
    implementation("org.apache.zookeeper:zookeeper:3.4.9")

    // We use log4j over slf4j
    implementation("org.slf4j:log4j-over-slf4j:1.7.10")
}
// end::dependencies[]

// tag::use_highest_asm[]
configurations.configureEach {
    resolutionStrategy.capabilitiesResolution.withCapability("org.ow2.asm:asm") {
        selectHighestVersion()
    }
}
// end::use_highest_asm[]

// tag::declare_capability[]
dependencies {
    // Activate the "LoggingCapability" rule
    components.all(LoggingCapability::class.java)
}

class LoggingCapability : ComponentMetadataRule {
    val loggingModules = setOf("log4j", "log4j-over-slf4j")

    override
    fun execute(context: ComponentMetadataContext) = context.details.run {
        if (loggingModules.contains(id.name)) {
            allVariants {
                withCapabilities {
                    // Declare that both log4j and log4j-over-slf4j provide the same capability
                    addCapability("log4j", "log4j", id.version)
                }
            }
        }
    }
}
// end::declare_capability[]

// tag::fix_asm[]
class AsmCapability : ComponentMetadataRule {
    override
    fun execute(context: ComponentMetadataContext) = context.details.run {
        if (id.group == "asm" && id.name == "asm") {
            allVariants {
                withCapabilities {
                    // Declare that ASM provides the org.ow2.asm:asm capability, but with an older version
                    addCapability("org.ow2.asm", "asm", id.version)
                }
            }
        }
    }
}
// end::fix_asm[]

if (project.hasProperty("replace")) {

    // tag::use_slf4j[]
    configurations.configureEach {
        resolutionStrategy.capabilitiesResolution.withCapability("log4j:log4j") {
            val toBeSelected = candidates.firstOrNull { it.id.let { id -> id is ModuleComponentIdentifier && id.module == "log4j-over-slf4j" } }
            if (toBeSelected != null) {
                select(toBeSelected)
            }
            because("use slf4j in place of log4j")
        }
    }
    // end::use_slf4j[]
}
