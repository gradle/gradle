import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.TestSuiteType
import org.gradle.api.attributes.VerificationType
import org.gradle.kotlin.dsl.*

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

/*
 * Should be applied to projects that represent a Gradle platform.
 * This plugin adds tasks that aggregate actions across all subprojects of the platform.
 * This plugin should eventually be expanded to handle other platform-scope respoonsibilities,
 * for example distribution/packaging.
 */

val subproject = configurations.dependencyScope("subproject")

val resolvedSubprojects = configurations.resolvable("resolvedSubprojects") {
    extendsFrom(subproject.get())
    // We do not want to depend on the dependencies of our subprojects.
    // The intention is to only run the tests of this platform only.
    isTransitive = false
}

tasks.register("test") {
    dependsOn(resolvedSubprojects.get().incoming.artifactView {
        withVariantReselection()
        componentFilter { it is ProjectComponentIdentifier }
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.VERIFICATION))
            attribute(TestSuiteType.TEST_SUITE_TYPE_ATTRIBUTE, objects.named(TestSuiteType::class.java, "unit-test"))
            attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType::class.java, VerificationType.TEST_RESULTS))
        }
    }.files)
}

// In order to support embeddedIntegTest, etc, we would need to use test suites to implement
// our own integration testing. That way, there would be a variant exposed with the integ
// test results. Alternatively, we could expose that variant manually, though it would probably
// be better to dogfood our own integ test plugin.
