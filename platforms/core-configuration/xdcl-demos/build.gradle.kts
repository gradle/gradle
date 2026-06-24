import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.javadoc.Javadoc

plugins {
    id("gradlebuild.internal.java")
    id("xdcl-gradle-plugin")
}

description = "Demos of XDCL-based project types, schemas, reactions, and defaults"

// xdcl-gradle-plugin wires its generated facades into the main source set. They are build artifacts
// (regenerated every build, never committed), so the style/header/javadoc gates that apply to
// authored source must not see them.
tasks.withType<Checkstyle>().configureEach {
    exclude { it.file.absolutePath.contains("/generated/xdcl/") }
}
tasks.withType<Javadoc>().configureEach {
    exclude { it.file.absolutePath.contains("/generated/xdcl/") }
}

dependencies {
    api(projects.coreApi)
    api(projects.baseServices) // Named, Describable, capitalize and other base API the facades/model expose

    // org.gradle.api.xdcl.* (Reaction, ReactionScope, Facade, ConfigurationNode, ...) is part of the
    // Gradle API in the distribution-under-test at runtime; at build time it must be on the compile
    // classpath only, so the demo jar does not bundle a second copy (classloader identity, embedded executer).
    compileOnly(libs.xdclGradleApi)

    // Real task types the reaction registers live in distinct subprojects (bundled via implementation,
    // matching project-features-demos — implementation deps are not packed into the jar):
    implementation(projects.core)         // Copy, project internals
    implementation(projects.languageJava)  // JavaCompile
    implementation(projects.languageGroovy) // GroovyCompile
    implementation(projects.languageJvm)   // AbstractCompile source/target compatibility
    implementation(projects.platformJvm)   // org.gradle.jvm.tasks.Jar
    implementation(projects.testingJvm)    // org.gradle.api.tasks.testing.Test
    implementation(projects.testingBase)   // Test report containers
    implementation(projects.reporting)     // DirectoryReport output locations
    implementation(projects.loggingApi)    // project.getLogger()

    integTestImplementation(projects.coreApi)
    integTestImplementation(projects.logging)
    integTestImplementation(projects.internalTesting)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
