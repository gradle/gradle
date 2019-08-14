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
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":snapshots"))
    implementation(project(":modelCore"))
    implementation(project(":fileCollections"))
    implementation(project(":dependencyManagement"))
    // TODO - move the isolatable serializer to model-core to live with the isolatable infrastructure
    implementation(project(":workers"))

    implementation(library("groovy"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))

    implementation(futureKotlin("stdlib-jdk8"))
    implementation(futureKotlin("reflect"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testLibrary("mockito_kotlin2"))

    integTestImplementation(project(":toolingApi"))

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

    testRuntimeOnly(kotlin("reflect"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

