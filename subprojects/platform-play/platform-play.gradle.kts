import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

dependencies {
    compile(project(":core"))
    compile(project(":platformJvm"))
    compile(project(":languageJvm"))
    compile(project(":languageScala"))
    compile(project(":javascript"))
    compile(project(":diagnostics"))

    integTestRuntime(project(":compositeBuilds"))
    integTestRuntime(project(":idePlay"))
    testFixturesCompile(project(":internalIntegTesting"))
    testFixturesCompile(library("commons_httpclient"))
}

gradlebuildJava {
    // Code needs to run in the compiler daemon
    moduleType = ModuleType.WORKER
}

testFixtures {
    from(":core")
    from(":languageScala", "integTest")
    from(":languageJava", "integTest")
    from(":languageJvm", "testFixtures")
    from(":launcher", "testFixtures")
    from(":dependencyManagement")
    from(":diagnostics")
    from(":platformBase")
}

tasks.withType<IntegrationTest>().named("integTest").configure {
    exclude("org/gradle/play/prepare/**")
}

val integTestPrepare = tasks.register<IntegrationTest>("integTestPrepare") {
    systemProperties["org.gradle.integtest.executer"] = "embedded"
    if (BuildEnvironment.isCiServer) {
        systemProperties["org.gradle.integtest.multiversion"] = "all"
    }
    include("org/gradle/play/prepare/**")
    maxParallelForks = 1
}

tasks.withType<IntegrationTest>() {
    if (name != "integTestPrepare") {
        dependsOn(integTestPrepare)
    }
}
