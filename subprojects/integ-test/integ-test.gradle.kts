import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    integTestImplementation(project(":baseServices"))
    integTestImplementation(project(":native"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":processServices"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(project(":resources"))
    integTestImplementation(project(":persistentCache"))
    integTestImplementation(project(":dependencyManagement"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(library("groovy"))
    integTestImplementation(library("slf4j_api"))
    integTestImplementation(library("guava"))
    integTestImplementation(library("ant"))
    integTestImplementation(testLibrary("jsoup"))
    integTestImplementation(testLibrary("jetty"))
    integTestImplementation(testLibrary("sampleCheck")) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
        exclude(module = "slf4j-simple")
    }

    val allTestRuntimeDependencies: DependencySet by rootProject.extra
    allTestRuntimeDependencies.forEach {
        integTestRuntimeOnly(it)
    }

    crossVersionTestImplementation(project(":baseServices"))
    crossVersionTestImplementation(project(":core"))
    crossVersionTestImplementation(project(":plugins"))
    crossVersionTestImplementation(project(":platformJvm"))
    crossVersionTestImplementation(project(":languageJava"))
    crossVersionTestImplementation(project(":languageGroovy"))
    crossVersionTestImplementation(project(":scala"))
    crossVersionTestImplementation(project(":ear"))
    crossVersionTestImplementation(project(":testingJvm"))
    crossVersionTestImplementation(project(":ide"))
    crossVersionTestImplementation(project(":codeQuality"))
    crossVersionTestImplementation(project(":signing"))

    allTestRuntimeDependencies.forEach {
        crossVersionTestRuntimeOnly(it)
    }
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

testFixtures {
    from(":core", "integTest")
    from(":diagnostics", "integTest")
    from(":platformNative", "integTest")
}

val integTestTasks: DomainObjectCollection<IntegrationTest> by extra
integTestTasks.configureEach {
    libsRepository.required = true
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
