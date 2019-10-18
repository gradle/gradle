import build.futureKotlin
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.testing.performance.generator.tasks.RemoteProject

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
    implementation(project(":persistentCache"))
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


tasks {

    /**
     * Santa Tracker git URI.
     *
     * Note that you can change it to `file:///path/to/your/santa-tracker-clone/.git`
     * if you need to iterate quickly on changes to Santa Tracker.
     */
    val gitUri = "https://github.com/gradle/santa-tracker-android.git"

    val santaTrackerJava by registering(RemoteProject::class) {
        remoteUri.set(gitUri)
        // From agp-3.6.0-java branch
        ref.set("b71104d9f644a21f8056deea4d3703fac58c3a72")
    }

    val santaTrackerKotlin by registering(RemoteProject::class) {
        remoteUri.set(gitUri)
        // From agp-3.6.0 branch
        ref.set("036aad22af993d2f564a6a15d6a7b9706ba37d8e")
    }

    if (BuildEnvironment.isCiServer) {
        withType<RemoteProject>().configureEach {
            outputs.upToDateWhen { false }
        }
    }

    withType<IntegrationTest>().configureEach {
        dependsOn(santaTrackerJava)
        dependsOn(santaTrackerKotlin)
        inputs.property("androidHomeIsSet", System.getenv("ANDROID_HOME") != null)
    }

    register<Delete>("cleanRemoteProjects") {
        delete(santaTrackerJava.get().outputDirectory)
        delete(santaTrackerKotlin.get().outputDirectory)
    }
}
