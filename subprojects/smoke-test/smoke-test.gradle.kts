import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.gradlebuild.test.integrationtests.SmokeTest

sourceSets {
    val main by existing
    create("smokeTest") {
        compileClasspath += main.output
//        runtimeClasspath += main.output
    }
}

configurations {
//    smokeTestCompile.extendsFrom(testCompile)
//    smokeTestRuntime.extendsFrom(testRuntime)
//    partialDistribution.extendsFrom(smokeTestRuntimeClasspath)
}

dependencies {
//    smokeTestCompile(project(":testKit"))
//    smokeTestCompile(project(":internalIntegTesting"))
//    smokeTestCompile(testLibrary("spock"))
//
//    smokeTestRuntimeOnly("org.gradle:gradle-kotlin-dsl:${BuildEnvironment.gradleKotlinDslVersion}")
//    smokeTestRuntimeOnly(project(":codeQuality"))
//    smokeTestRuntimeOnly(project(":ide"))
//    smokeTestRuntimeOnly(project(":ivy"))
//    smokeTestRuntimeOnly(project(":jacoco"))
//    smokeTestRuntimeOnly(project(":maven"))
//    smokeTestRuntimeOnly(project(":plugins"))
//    smokeTestRuntimeOnly(project(":pluginDevelopment"))
//    smokeTestRuntimeOnly(project(":toolingApiBuilders"))
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

testFixtures {
    from(":core")
    from(":versionControl")
}

tasks.named<Copy>("processSmokeTestResources") {
    from("$rootDir/gradle/init-scripts") {
        into("org/gradle/smoketests/cache-init-scripts")
        include("overlapping-task-outputs-stats-init.gradle")
    }
}

tasks.register("smokeTest", SmokeTest::class) {
    group = "Verification"
    description = "Runs Smoke tests"
//    testClassesDirs = sourceSets.smokeTest.output.classesDirs
//    classpath = sourceSets.smokeTest.runtimeClasspath
    maxParallelForks = 1 // those tests are pretty expensive, we shouldn't execute them concurrently
}

plugins.withType<IdeaPlugin>().configureEach { // lazy as plugin not applied yet
//    val sourceSet = sourceSets.smokeTest
    idea {
        module {
//            testSourceDirs = testSourceDirs + sourceSet.groovy.srcDirs
//            testResourceDirs = testResourceDirs + sourceSet.resources.srcDirs
//            scopes.TEST.plus.add(configurations.smokeTestCompile)
//            scopes.TEST.plus.add(configurations.smokeTestRuntime)
        }
    }
}

plugins.withType<EclipsePlugin>().configureEach { // lazy as plugin not applied yet
    eclipse {
        classpath {
//            plusConfigurations.add(configurations.smokeTestCompile)
//            plusConfigurations.add(configurations.smokeTestRuntime)
        }
    }
}
