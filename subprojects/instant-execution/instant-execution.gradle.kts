import build.futureKotlin
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `kotlin-library`
}

val reportResources by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("report-resources"))
    }
}

tasks {
    processResources {
        from(reportResources) {
            into("org/gradle/instantexecution")
        }
    }
}

dependencies {
    reportResources(project(":instantExecutionReport"))

    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":modelCore"))
    implementation(project(":files"))

    implementation(library("groovy"))
    implementation(library("slf4j_api"))

    implementation(futureKotlin("stdlib-jdk8"))
    implementation(futureKotlin("reflect"))

    testImplementation(testFixtures(project(":core")))

    integTestImplementation(project(":toolingApi"))

    integTestImplementation(library("guava"))
    integTestImplementation(library("ant"))
    integTestImplementation(library("inject"))
    integTestImplementation(testFixtures(project(":dependencyManagement")))

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

