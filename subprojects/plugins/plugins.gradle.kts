import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.unittestandcompile.ModuleType

/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

dependencies {
    compile(library("groovy"))

    compile(project(":core"))
    compile(project(":workers"))
    compile(project(":dependencyManagement"))
    compile(project(":reporting"))
    compile(project(":platformJvm"))
    compile(project(":languageJvm"))
    compile(project(":languageJava"))
    compile(project(":languageGroovy"))
    compile(project(":diagnostics"))
    compile(project(":testingJvm"))
    compile(project(":snapshots"))

    compile(library("ant"))
    compile(library("asm"))
    compile(library("commons_io"))
    compile(library("commons_lang"))
    compile(library("slf4j_api"))

    // This dependency makes the services provided by `:compositeBuilds` available at runtime for all integration tests in all subprojects
    // Making this better would likely involve a separate `:gradleRuntime` module that brings in `:core`, `:dependencyManagement` and other key subprojects
    runtime(project(":compositeBuilds"))

    testFixturesApi(project(":internalIntegTesting"))

    testCompile(testLibrary("jsoup"))

    integTestRuntime(project(":maven"))
}


gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":core", "testFixtures")
    from(":dependencyManagement")
    from(":languageJava")
    from(":languageGroovy")
    from(":diagnostics")
}

val wrapperJarDir = file("$buildDir/generated-resources/wrapper-jar")
evaluationDependsOn(":wrapper")
val wrapperJar by tasks.registering(Copy::class) {
    from(project(":wrapper").tasks.named("executableJar"))
    into(wrapperJarDir)
}
sourceSets.main {
    output.dir(wrapperJarDir, "builtBy" to wrapperJar)
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
