import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.strict-compile")
}

dependencies {
    compile(project(":core"))
    compile(project(":toolingApi"))
    compile(library("commons_io"))
    runtime(project(":native"))
    integTestRuntime(project(":toolingApiBuilders"))
    integTestRuntime(project(":pluginDevelopment"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
}

tasks.register<IntegrationTest>("crossVersionTests") {
    setDescription("Runs the TestKit version compatibility tests")
    systemProperties["org.gradle.integtest.testkit.compatibility"] = "all"
    systemProperties["org.gradle.integtest.executer"] = "forking"
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
