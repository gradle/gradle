repositories {
    ivy {
        url = uri("file://${projectDir}/repo")
    }
}

// Set up the status scheme so that "experimental" is a valid status for "org.sample" artifacts

class StatusRule : ComponentMetadataRule {

    override fun execute(componentMetadataContext: ComponentMetadataContext) {
        val details = componentMetadataContext.details
        if (details.id.group == "org.sample") {
            details.statusScheme = listOf("experimental", "integration", "milestone", "release")
        }
    }
}
dependencies {
    components {
        all(StatusRule::class.java)
    }
}

// tag::reject-version-1-1[]
configurations {
    create("rejectConfig") {
        resolutionStrategy {
            componentSelection {
                // Accept the highest version matching the requested version that isn't '1.5'
                all {
                    if (candidate.group == "org.sample" && candidate.module == "api" && candidate.version == "1.5") {
                        reject("version 1.5 is broken for 'org.sample:api'")
                    }
                }
            }
        }
    }
}

dependencies {
    "rejectConfig"("org.sample:api:1.+")
}
// end::reject-version-1-1[]

tasks.register("printRejectConfig") {
    doLast {
        configurations["rejectConfig"].forEach { println("Resolved: ${it.name}") }
    }
}

// tag::component-selection-with-metadata[]
configurations {
    create("metadataRulesConfig") {
        resolutionStrategy {
            componentSelection {
                // Reject any versions with a status of 'experimental'
                all {
                    if (candidate.group == "org.sample" && metadata?.status == "experimental") {
                        reject("don't use experimental candidates from 'org.sample'")
                    }
                }
                // Accept the highest version with either a "release" branch or a status of 'milestone'
                withModule("org.sample:api") {
                    if (getDescriptor(IvyModuleDescriptor::class)?.branch != "release" && metadata?.status != "milestone") {
                        reject("'org.sample:api' must have testing branch or milestone status")
                    }
                }
            }
        }
    }
}
// end::component-selection-with-metadata[]

dependencies {
    "metadataRulesConfig"("org.sample:api:1.+")
    "metadataRulesConfig"("org.sample:lib:+")
}

tasks.register("printMetadataRulesConfig") {
    doLast {
        configurations["metadataRulesConfig"].forEach { println("Resolved: ${it.name}") }
    }
}

// tag::targeted-component-selection[]
configurations {
    create("targetConfig") {
        resolutionStrategy {
            componentSelection {
                withModule("org.sample:api") {
                    if (candidate.version == "1.5") {
                        reject("version 1.5 is broken for 'org.sample:api'")
                    }
                }
            }
        }
    }
}
// end::targeted-component-selection[]

dependencies {
    "targetConfig"("org.sample:api:1.+")
}

tasks.register("printTargetConfig") {
    doLast {
        configurations["targetConfig"].forEach { println("Resolved: ${it.name}") }
    }
}

// tag::api-component-selection[]
class RejectTestBranch {
    @Mutate
    fun evaluateRule(selection: ComponentSelection) {
        if (selection.getDescriptor(IvyModuleDescriptor::class)?.branch == "test") {
            selection.reject("reject test branch")
        }
    }
}

configurations {
    create("ruleSourceConfig") {
        resolutionStrategy {
            componentSelection {
                all(RejectTestBranch())
            }
        }
    }
}
// end::api-component-selection[]

dependencies {
    "ruleSourceConfig"("org.sample:api:1.+")
}

tasks.register("printRuleSourceConfig") {
    doLast {
        configurations["ruleSourceConfig"].forEach { println("Resolved: ${it.name}") }
    }
}

configurations {
    create("sampleConfig") {
        resolutionStrategy {
            componentSelection {
                withModule("org.sample:api") {
                    // Veto everything except patch releases
                    if (candidate.version.matches("""\d+.\d+\.\d+""".toRegex())) {
                        logger.lifecycle("** Accepted version: ${candidate.version} **")
                    } else {
                        logger.lifecycle("Rejected version: ${candidate.version}")
                        reject("Version is broken")
                    }
                }
            }
        }
    }
}

dependencies {
    "sampleConfig"(group = "org.sample", name = "api", version = "1+")
}

tasks.register("resolveConfiguration") {
    doLast {
        configurations["sampleConfig"].forEach { println(it) }
    }
}
