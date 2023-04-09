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

package org.gradle.testing.junitvintage

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.junit.AbstractJUnit4CategoriesOrTagsCoverageIntegrationTest

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE

@TargetCoverage({ JUNIT_VINTAGE })
class JUnitVintageCategoriesOrTagsCoverageIntegrationTest extends AbstractJUnit4CategoriesOrTagsCoverageIntegrationTest {
    String testFrameworkConfigurationMethod = "useJUnitPlatform()"
    String singularCategoryOrTagName = "tag"
    String pluralCategoryOrTagName = "tags"

    @Override
    List<String> getTestFrameworkImplementationDependencies() {
        return ["junit:junit:4.13"]
    }

    @Override
    List<String> getTestFrameworkRuntimeDependencies() {
        return ["org.junit.vintage:junit-vintage-engine:${dependencyVersion}"]
    }

    private String getDependencyVersion() {
        return version.toString().substring("Vintage:".length())
    }

    @Override
    String includeCategoryOrTag(String categoryOrTag) {
        return "includeTags '$categoryOrTag'"
    }

    @Override
    String excludeCategoryOrTag(String categoryOrTag) {
        return "excludeTags '$categoryOrTag'"
    }

    @Override
    boolean supportsCategoryOnNestedClass() {
        return true
    }
}
