import org.gradle.gradlebuild.testing.integrationtests.cleanup.EmptyDirectoryCheck
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    id("gradlebuild.classycle")
}

dependencies {
    api(project(":core"))
    api(project(":publish"))
    api(project(":plugins")) // for base plugin to get archives conf
    api(project(":pluginUse"))
    api(project(":dependencyManagement"))

    implementation(library("ivy"))

    integTestImplementation(project(":ear"))
    integTestRuntimeOnly(project(":resourcesS3"))
    integTestRuntimeOnly(project(":resourcesSftp"))
    testFixturesImplementation(project(":internalIntegTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.PLUGIN
}

testFixtures {
    from(":core")
    from(":modelCore")
    from(":platformBase")
}

testFilesCleanup {
    isErrorWhenNotEmpty = false
}
