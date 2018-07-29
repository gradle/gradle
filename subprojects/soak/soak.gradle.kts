import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.test.integrationtests.SoakTest
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    id("gradlebuild.classycle")
}

dependencies {
    testFixturesCompile(project(":internalIntegTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

testFixtures {
    from(":core")
}

tasks.withType<IntegrationTest>().matching {
    listOf("integTest", "java9IntegTest").contains(it.name)
}.configureEach {
    options {
        (this as JUnitOptions).excludeCategories("org.gradle.soak.categories.SoakTest")
    }
}

tasks.register<SoakTest>("soakTest") {
    testClassesDirs = sourceSets.getByName("integTest").output.classesDirs
    classpath = sourceSets.getByName("integTest").runtimeClasspath
    systemProperties["org.gradle.soaktest"] = "true"
    options {
        (this as JUnitOptions).includeCategories("org.gradle.soak.categories.SoakTest")
    }
}
