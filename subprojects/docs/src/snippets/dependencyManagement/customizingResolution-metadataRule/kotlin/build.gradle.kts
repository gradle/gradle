plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::config-component-metadata-rule[]
@CacheableRule
abstract class TargetJvmVersionRule @Inject constructor(val jvmVersion: Int) : ComponentMetadataRule {
    @get:Inject abstract val objects: ObjectFactory

    override fun execute(context: ComponentMetadataContext) {
        context.details.withVariant("compile") {
            attributes {
                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, jvmVersion)
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
            }
        }
    }
}
dependencies {
    components {
        withModule<TargetJvmVersionRule>("commons-io:commons-io") {
            params(7)
        }
        withModule<TargetJvmVersionRule>("commons-collections:commons-collections") {
            params(8)
        }
    }
    implementation("commons-io:commons-io:2.6")
    implementation("commons-collections:commons-collections:3.2.2")
}
// end::config-component-metadata-rule[]


// tag::jaxen-rule-1[]
@CacheableRule
abstract class JaxenDependenciesRule: ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { it.group in listOf("dom4j", "jdom", "xerces",  "maven-plugins", "xml-apis", "xom") }
            }
        }
    }
}
// end::jaxen-rule-1[]

// tag::jaxen-rule-2[]
@CacheableRule
abstract class JaxenCapabilitiesRule: ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.addVariant("runtime-dom4j", "runtime") {
            withCapabilities {
                removeCapability("jaxen", "jaxen")
                addCapability("jaxen", "jaxen-dom4j", context.details.id.version)
            }
            withDependencies {
                add("dom4j:dom4j:1.6.1")
            }
        }
    }
}
// end::jaxen-rule-2[]

// tag::jaxen-dependencies[]
dependencies {
    components {
        withModule<JaxenDependenciesRule>("jaxen:jaxen")
        withModule<JaxenCapabilitiesRule>("jaxen:jaxen")
    }
    implementation("jaxen:jaxen:1.1.3")
    runtimeOnly("jaxen:jaxen:1.1.3") {
        capabilities { requireCapability("jaxen:jaxen-dom4j") }
    }
}
// end::jaxen-dependencies[]


// tag::guava-rule[]
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
// end::guava-rule[]

// tag::guava-dependencies[]
configurations["compileClasspath"].attributes {
    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 6)
}
dependencies {
    components {
        withModule<GuavaRule>("com.google.guava:guava")
    }
    // '23.3-android' and '23.3-jre' are now the same as both offer both variants
    implementation("com.google.guava:guava:23.3+")
}
// end::guava-dependencies[]


// tag::quasar-rule[]
@CacheableRule
abstract class QuasarRule: ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        listOf("compile", "runtime").forEach { base ->
            context.details.addVariant("jdk8${base.capitalize()}", base) {
                attributes {
                    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                }
                withFiles {
                    removeAllFiles()
                    addFile("${context.details.id.name}-${context.details.id.version}-jdk8.jar")
                }
            }
            context.details.withVariant(base) {
                attributes {
                    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 7)
                }
            }
        }
    }
}
// end::quasar-rule[]

// tag::quasar-dependencies[]
configurations["compileClasspath"].attributes {
    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
}
dependencies {
    components {
        withModule<QuasarRule>("co.paralleluniverse:quasar-core")
    }
    implementation("co.paralleluniverse:quasar-core:0.7.9")
}
// end::quasar-dependencies[]


// tag::lwgj-rule[]
@CacheableRule
abstract class LwjglRule: ComponentMetadataRule {
    data class NativeVariant(val os: String, val arch: String, val classifier: String)

    private val nativeVariants = listOf(
        NativeVariant(OperatingSystemFamily.LINUX,   "arm32",  "natives-linux-arm32"),
        NativeVariant(OperatingSystemFamily.LINUX,   "arm64",  "natives-linux-arm64"),
        NativeVariant(OperatingSystemFamily.WINDOWS, "x86",    "natives-windows-x86"),
        NativeVariant(OperatingSystemFamily.WINDOWS, "x86-64", "natives-windows"),
        NativeVariant(OperatingSystemFamily.MACOS,   "x86-64", "natives-macos")
    )

    @get:Inject abstract val objects: ObjectFactory

    override fun execute(context: ComponentMetadataContext) {
        context.details.withVariant("runtime") {
            attributes {
                attributes.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named("none"))
                attributes.attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named("none"))
            }
        }
        nativeVariants.forEach { variantDefinition ->
            context.details.addVariant("${variantDefinition.classifier}-runtime", "runtime") {
                attributes {
                    attributes.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named(variantDefinition.os))
                    attributes.attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named(variantDefinition.arch))
                }
                withFiles {
                    addFile("${context.details.id.name}-${context.details.id.version}-${variantDefinition.classifier}.jar")
                }
            }
        }
    }
}
// end::lwgj-rule[]

// tag::lwgj-dependencies[]
configurations["runtimeClasspath"].attributes {
    attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named("windows"))
}
dependencies {
    components {
        withModule<LwjglRule>("org.lwjgl:lwjgl")
    }
    implementation("org.lwjgl:lwjgl:3.2.3")
}
// end::lwgj-dependencies[]


// tag::guice-rule[]
@CacheableRule
abstract class GuiceRule: ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        listOf("compile", "runtime").forEach { base ->
            context.details.addVariant("noAop${base.capitalize()}", base) {
                withCapabilities {
                    addCapability("com.google.inject", "guice-no_aop", context.details.id.version)
                }
                withFiles {
                    removeAllFiles()
                    addFile("guice-${context.details.id.version}-no_aop.jar")
                }
                withDependencies {
                    removeAll { it.group == "aopalliance" }
                }
            }
        }
    }
}
// end::guice-rule[]

// tag::guice-dependencies[]
dependencies {
    components {
        withModule<GuiceRule>("com.google.inject:guice")
    }
    implementation("com.google.inject:guice:4.2.2") {
        capabilities { requireCapability("com.google.inject:guice-no_aop") }
    }
}
// end::guice-dependencies[]


// tag::ivy-component-metadata-rule[]
@CacheableRule
abstract class IvyComponentRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        val descriptor = context.getDescriptor(IvyModuleDescriptor::class)
        if (descriptor != null && descriptor.branch == "testing") {
            context.details.status = "rc"
        }
    }
}
// end::ivy-component-metadata-rule[]

// tag::maven-packaging-component-metadata-rule[]
@CacheableRule
abstract class MavenComponentRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        val descriptor = context.getDescriptor(PomModuleDescriptor::class)
        if (descriptor != null && descriptor.packaging == "war") {
            // ...
        }
    }
}
// end::maven-packaging-component-metadata-rule[]

dependencies {
    components {
        all<IvyComponentRule>()
        all<MavenComponentRule>()
    }
}

// tag::custom-status-scheme[]
@CacheableRule
abstract class CustomStatusRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.statusScheme = listOf("nightly", "milestone", "rc", "release")
        if (context.details.status == "integration") {
            context.details.status = "nightly"
        }
    }
}

dependencies {
    components {
        all<CustomStatusRule>()
    }
    implementation("org.apache.commons:commons-lang3:latest.rc")
}
// end::custom-status-scheme[]

tasks.register("compileClasspathArtifacts") {
    val compileClasspath: FileCollection = configurations["compileClasspath"]
    doLast {
        compileClasspath.filter { !it.name.startsWith("commons-lang3") }.forEach { println(it.name) }
    }
}
tasks.register("failRuntimeClasspathResolve") {
    val runtimeClasspath: FileCollection = configurations["runtimeClasspath"]
    doLast {
        runtimeClasspath.forEach { println(it.name) }
    }
}
tasks.register("runtimeClasspathArtifacts") {
    val runtimeClasspath: FileCollection = configurations["runtimeClasspath"]
    configurations["runtimeClasspath"].attributes {
        attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named("x86"))
    }
    doLast {
        runtimeClasspath.filter { !it.name.startsWith("commons-lang3") }.forEach { println(it.name) }
    }
}
