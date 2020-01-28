import build.futureKotlin
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `kotlin-library`
}

tasks {
    processResources {
        from({ project(":instantExecutionReport").tasks.named("assembleReport") }) {
            into("org/gradle/instantexecution")
        }
    }
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":baseServicesGroovy"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":snapshots"))
    implementation(project(":modelCore"))
    implementation(project(":fileCollections"))
    implementation(project(":dependencyManagement"))
    implementation(project(":persistentCache"))
    implementation(project(":kotlinDsl"))
    // TODO - move the isolatable serializer to model-core to live with the isolatable infrastructure
    implementation(project(":workers"))
    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    implementation(project(":toolingApi"))
    implementation(project(":buildEvents"))

    implementation(library("groovy"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))

    implementation(futureKotlin("stdlib-jdk8"))
    implementation(futureKotlin("reflect"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testLibrary("mockito_kotlin2"))

    testRuntimeOnly(project(":runtimeApiInfo"))
    testRuntimeOnly(kotlin("reflect"))

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


tasks {
    instantIntegTest {
        enabled = false
    }
}
