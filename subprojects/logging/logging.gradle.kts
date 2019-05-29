import org.gradle.gradlebuild.unittestandcompile.ModuleType

/**
 * Logging infrastructure.
 */
plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":cli"))
    implementation(project(":buildOption"))
    implementation(library("slf4j_api"))

    implementation(project(":native"))
    implementation(library("jul_to_slf4j"))
    implementation(library("ant"))
    implementation(library("commons_lang"))
    implementation(library("guava"))
    implementation(library("jansi"))

    runtimeOnly(library("log4j_to_slf4j"))
    runtimeOnly(library("jcl_to_slf4j"))

    integTestImplementation(library("ansi_control_sequence_util"))

    integTestRuntimeOnly(project(":apiMetadata"))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}

testFixtures {
    from(":core")
}

classycle {
    excludePatterns.set(listOf("org/gradle/internal/featurelifecycle/**"))
}
