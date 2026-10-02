/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.fixtures.executer;

/**
 * The deprecations Gradle emits when a problem is reported with an identity created outside the predefined problem
 * group hierarchy. Third-party plugins under test, such as the Kotlin Gradle plugin, still report through the legacy
 * {@code ProblemGroup.create()} API, so test harnesses tolerate these deprecations by default; the tests that cover the
 * deprecations themselves opt out of the filtering.
 */
public final class ProblemsApiDeprecations {

    /**
     * Every deprecation of this family links to an upgrading guide section with this prefix.
     */
    public static final String UPGRADE_GUIDE_ANCHOR_PREFIX = "upgrading_version_9.html#problems_api_";

    /**
     * The id of the problem the deprecation logger reports for the legacy identity deprecation.
     */
    public static final String LEGACY_IDENTITY_PROBLEM_FQID = "Gradle:Deprecation:Problem reported with a group created through ProblemGroup.create()";

    private ProblemsApiDeprecations() {
    }

    public static ExpectedDeprecationWarning anyLegacyIdentityDeprecation() {
        return ExpectedDeprecationWarning.withLineContaining(UPGRADE_GUIDE_ANCHOR_PREFIX);
    }
}
