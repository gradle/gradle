plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

/*
// tag::string-notation[]
dependencies {
    // GOOD: single-string notation
    implementation("com.google.guava:guava:32.1.2-jre")
    // BAD: map notation
    implementation(group = "com.google.guava", name = "guava", version = "32.1.2-jre")
}
// end::string-notation[]
*/

// tag::conflict-deps[]
dependencies {
    implementation("jaxen:jaxen:1.1.6")     // Transitive dependency that brings XPath functionality
    implementation("org.jdom:jdom2:2.0.6")  // Also offers XPath functionality
}
// end::conflict-deps[]

// tag::capability-deps[]
dependencies {
    implementation("jaxen:jaxen:1.1.6") {
        capabilities {
            requireCapability("xml:xpath-support")
        }
    }
    implementation("org.jdom:jdom2:2.0.6")
}
// end::capability-deps[]

// tag::metadata-rule[]
dependencies {
    components {
        withModule("jaxen:jaxen") {
            allVariants {
                withCapabilities {
                    addCapability("xml", "xpath-support", "1.0")
                }
            }
        }
        withModule("org.jdom:jdom2") {
            allVariants {
                withCapabilities {
                    addCapability("xml", "xpath-support", "1.0")
                }
            }
        }
    }
}
// end::metadata-rule[]
