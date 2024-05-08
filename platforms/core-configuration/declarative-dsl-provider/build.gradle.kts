/*
 * Copyright 2023 the original author or authors.
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
    id("gradlebuild.distribution.implementation-kotlin")
}

dependencies {
    api(projects.serviceProvider)
    api(project(":core"))
    api(project(":core-api"))
    api(project(":declarative-dsl-api"))
    api(project(":declarative-dsl-core"))
    api(project(":declarative-dsl-evaluator"))
    api(project(":declarative-dsl-tooling-models"))
    api(libs.kotlinStdlib)


    implementation(libs.inject)
    testImplementation(libs.mockitoKotlin2)

    implementation(project(":base-services"))
    implementation(project(":resources"))
    implementation(project(":model-core"))

    implementation(libs.guava)
    implementation(libs.kotlinReflect)

    integTestImplementation(project(":internal-testing"))
    integTestImplementation(project(":logging"))
    integTestImplementation(testFixtures(project(":declarative-dsl-provider")))

    testFixturesImplementation(project(":internal-testing"))
    testFixturesImplementation(project(":internal-integ-testing"))

    integTestDistributionRuntimeOnly(project(":distributions-full"))

    integTestImplementation(testFixtures(project(":tooling-api")))
}
