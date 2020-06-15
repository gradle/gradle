import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
/*
 * Copyright 2014 the original author or authors.
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
plugins {
    gradlebuild.distribution.`api-java`
}

val integTestRuntimeResources by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = false
}
val integTestRuntimeResourcesClasspath by configurations.creating {
    extendsFrom(integTestRuntimeResources)
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        // play test apps MUST be found as exploded directory
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements::class.java, LibraryElements.RESOURCES))
    }
    isTransitive = false
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":fileCollections"))
    implementation(project(":ide"))
    implementation(project(":languageScala"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":platformPlay"))

    implementation(library("groovy"))
    implementation(library("guava"))

    integTestImplementation(testFixtures(project(":platformPlay")))
    integTestImplementation(testFixtures(project(":ide")))

    integTestRuntimeResources(testFixtures(project(":platformPlay")))

    integTestDistributionRuntimeOnly(project(":distributionsFull"))
}

strictCompile {
    ignoreDeprecations() // Play support in Gradle core has been deprecated
}

tasks.withType<IntegrationTest>().configureEach {
    dependsOn(":platformPlay:integTestPrepare")
    // this is a workaround for which we need a better fix:
    // it sets the platform play test fixtures resources directory in front
    // of the classpath, so that we can find them when executing tests in
    // an exploded format, rather than finding them in the test fixtures jar
    classpath = integTestRuntimeResourcesClasspath + classpath
}
