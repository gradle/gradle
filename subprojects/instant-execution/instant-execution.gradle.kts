import build.futureKotlin
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-library`
}

tasks {

    withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs += listOf(
                "-XXLanguage:+NewInference",
                "-XXLanguage:+SamConversionForKotlinFunctions"
            )
        }
    }

    processResources {
        from({ project(":instantExecutionReport").tasks.named("assembleReport") }) {
            into("org/gradle/instantexecution")
        }
    }

    instantIntegTest {
        enabled = false
    }
}

afterEvaluate {
    // This is a workaround for the validate plugins task trying to inspect classes which have changed but are NOT tasks.
    // For the current project, we simply disable it since there are no tasks in there.
    tasks.withType<ValidatePlugins>().configureEach {
        enabled = false
    }
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":baseServicesGroovy"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":snapshots"))
    implementation(project(":modelCore"))
    implementation(project(":fileCollections"))
    implementation(project(":dependencyManagement"))
    implementation(project(":persistentCache"))
    implementation(project(":plugins"))
    implementation(project(":kotlinDsl"))
    // TODO - move the isolatable serializer to model-core to live with the isolatable infrastructure
    implementation(project(":workers"))
    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    implementation(project(":toolingApi"))
    implementation(project(":buildEvents"))
    implementation(project(":native"))

    implementation(library("groovy"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))

    implementation(futureKotlin("stdlib-jdk8"))
    implementation(futureKotlin("reflect"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testLibrary("mockito_kotlin2"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.3.3")

    testRuntimeOnly(project(":runtimeApiInfo"))

    integTestImplementation(project(":jvmServices"))
    integTestImplementation(project(":toolingApi"))
    integTestImplementation(project(":platformJvm"))

    integTestImplementation(library("guava"))
    integTestImplementation(library("ant"))
    integTestImplementation(library("inject"))
    integTestImplementation(testFixtures(project(":dependencyManagement")))
    integTestImplementation(testFixtures(project(":jacoco")))

    integTestRuntimeOnly(project(":apiMetadata"))
    integTestRuntimeOnly(project(":toolingApiBuilders"))
    integTestRuntimeOnly(project(":runtimeApiInfo"))
    integTestRuntimeOnly(project(":testingJunitPlatform"))
    integTestRuntimeOnly(project(":kotlinDsl"))
    integTestRuntimeOnly(project(":kotlinDslProviderPlugins"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}
