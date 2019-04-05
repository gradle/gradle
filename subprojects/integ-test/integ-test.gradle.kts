import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.classycle
}

dependencies {
    integTestCompile(library("groovy"))
    integTestCompile(library("ant"))
    integTestCompile(testLibrary("jsoup"))
    integTestCompile(testLibrary("sampleCheck")) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
        exclude(module = "slf4j-simple")
    }

    val allTestRuntimeDependencies: DependencySet by rootProject.extra
    allTestRuntimeDependencies.forEach {
        integTestRuntime(it)
    }

    crossVersionTestCompile(project(":scala"))
    crossVersionTestCompile(project(":ide"))
    crossVersionTestCompile(project(":codeQuality"))
    crossVersionTestCompile(project(":signing"))

    allTestRuntimeDependencies.forEach {
        crossVersionTestRuntime(it)
    }
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

testFixtures {
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
