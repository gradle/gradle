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
plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Shared classes for projects requiring GPG support"

dependencies {
    api(project(":core-api"))
    api(project(":resources"))
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":resources-http"))
    implementation(libs.guava)

    api(libs.bouncycastlePgp)

    implementation(libs.groovy) {
        because("Project.exec() depends on Groovy")
    }

    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.jetty)
    testFixturesImplementation(libs.jettyWebApp)
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(project(":internal-integ-testing"))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
}

classycle {
    excludePatterns.set(listOf("org/gradle/plugins/signing/type/pgp/**"))
}
