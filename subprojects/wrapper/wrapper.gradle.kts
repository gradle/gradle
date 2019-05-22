/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import java.util.jar.Attributes
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":cli"))

    testImplementation(project(":baseServices"))
    testImplementation(project(":native"))
    testImplementation(library("ant"))

    integTestImplementation(project(":logging"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(library("commons_io"))
    integTestImplementation(testLibrary("littleproxy"))
    integTestImplementation(testLibrary("jetty"))

    crossVersionTestImplementation(project(":logging"))
    crossVersionTestImplementation(project(":persistentCache"))
    crossVersionTestImplementation(project(":launcherStartup"))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}

testFixtures {
    from(":core")
}

tasks.register<Jar>("executableJar") {
    archiveFileName.set("gradle-wrapper.jar")
    manifest {
        attributes.remove(Attributes.Name.IMPLEMENTATION_VERSION.toString())
        attributes(Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle Wrapper")
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().allDependencies.withType<ProjectDependency>().filter { it.dependencyProject.extensions.findByType<SourceSetContainer>() != null }.map {
        it.dependencyProject.sourceSets.main.get().output
    })
}

val integTestTasks: DomainObjectCollection<IntegrationTest> by extra
integTestTasks.configureEach {
    binaryDistributions.binZipRequired = true
}
