/*
 * Copyright 2024 the original author or authors.
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
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.minified-jar")
}

description = "Entry point of the Gradle wrapper command"

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":cli"))
    implementation(project(":wrapper-shared"))

    testImplementation(project(":base-services"))
    testImplementation(testFixtures(project(":core")))

    integTestImplementation(project(":build-option"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":core-api"))
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.littleproxy)
    integTestImplementation(libs.jetty)

    crossVersionTestImplementation(project(":logging"))
    crossVersionTestImplementation(project(":persistent-cache"))
    crossVersionTestImplementation(project(":launcher"))

    integTestNormalizedDistribution(project(":distributions-full"))
    crossVersionTestNormalizedDistribution(project(":distributions-full"))

    integTestDistributionRuntimeOnly(project(":distributions-full"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-full"))
}

minify {
    implementationTitle = "Gradle Wrapper"

    // Exclude META-INF resources from Guava etc. added via transitive dependencies
    excludeFromDependencies("META-INF/*")

    // Exclude properties files that are not required
    exclude("gradle-*-classpath.properties")
    exclude("gradle-*-parameter-names.properties")
}

// https://github.com/gradle/gradle/issues/26658
// Before introducing gr8, wrapper jar is generated as build/libs/gradle-wrapper.jar and used in promotion build
// After introducing gr8, wrapper jar is generated as build/libs/gradle-wrapper-executable.jar and processed
//   by gr8, then the processed `gradle-wrapper.jar` need to be copied back to build/libs for promotion build
val copyGr8OutputJarAsGradleWrapperJar by tasks.registering(Copy::class) {
    from(minify.minifiedJar) {
        rename { "gradle-wrapper.jar" }
    }
    into(layout.buildDirectory.dir("libs"))
}

tasks.jar {
    from(minify.minifiedJar) {
        rename { "gradle-wrapper.jar" }
    }
    dependsOn(copyGr8OutputJarAsGradleWrapperJar)
}
