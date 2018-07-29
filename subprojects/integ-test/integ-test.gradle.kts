import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.classycle")
}

repositories {
    mavenLocal()
}

dependencies {
    integTestCompile(library("groovy"))
    integTestCompile(library("ant"))
    integTestCompile(testLibrary("jsoup"))
    integTestCompile(testLibrary("sampleCheck"))

    // TODO This makes the ugliness explicit and discoverable of what is otherwise hidden in an extra property.
    rootProject.configurations.testRuntime.allDependencies.forEach {
        integTestRuntime(it)
    }

    crossVersionTestCompile(project(":scala"))
    crossVersionTestCompile(project(":ide"))
    crossVersionTestCompile(project(":codeQuality"))
    crossVersionTestCompile(project(":signing"))

    // TODO This makes the ugliness explicit and discoverable of what is otherwise hidden in an extra property. Improve!
    rootProject.configurations.testRuntime.allDependencies.forEach {
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

tasks.withType<IntegrationTest>() {
    libsRepository.required = true
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
