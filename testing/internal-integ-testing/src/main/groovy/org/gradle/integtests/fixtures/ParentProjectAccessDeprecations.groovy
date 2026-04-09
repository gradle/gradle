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

package org.gradle.integtests.fixtures

import groovy.transform.SelfType

/**
 * Trait for tests that need to expect deprecation warnings for accessing
 * properties or methods from parent projects.
 */
@SelfType(HasGradleExecutor)
trait ParentProjectAccessDeprecations {

    private static final String UPGRADE_GUIDE_SECTION = "deprecated_accessing_parent_project_properties"
    private static final String UPGRADE_GUIDE_LINK = "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#${UPGRADE_GUIDE_SECTION}"

    /**
     * Returns deprecation message for implicit property access (e.g. bare {@code foo} in a build script).
     */
    static String implicitParentPropertyDeprecation(String propertyName, String childProject, String parentProject) {
        "Accessing a property from a parent project has been deprecated. This will fail with an error in Gradle 10. Property '${propertyName}' was not found in ${childProject} and was dynamically resolved from ${parentProject}. ${UPGRADE_GUIDE_LINK}"
    }

    /**
     * Returns deprecation message for explicit property access (e.g. {@code property("foo")}, {@code findProperty("foo")}).
     */
    static String explicitParentPropertyDeprecation(String callerApi, String propertyName, String childProject, String parentProject) {
        "Calling '${callerApi}' to retrieve property from parent project has been deprecated. This will fail with an error in Gradle 10. Tried to query parent project ${parentProject} for property '${propertyName}' from ${childProject}. ${UPGRADE_GUIDE_LINK}"
    }

    /**
     * Returns deprecation message for {@code hasProperty()} calls that resolve from a parent project.
     */
    static String parentHasPropertyDeprecation(String propertyName, String childProject, String parentProject) {
        "Calling 'hasProperty' to query presence of property from parent project has been deprecated. This will fail with an error in Gradle 10. Tried to query parent project ${parentProject} for presence property '${propertyName}' from ${childProject}. ${UPGRADE_GUIDE_LINK}"
    }

    /**
     * Returns deprecation message for invoking a method from a parent project.
     */
    static String parentMethodDeprecation(String methodName, String parentProject, String childProject) {
        "Dynamically invoking parent method from a child project has been deprecated. This will fail with an error in Gradle 10. Cannot dynamically invoke method '${methodName}' on ${parentProject} from ${childProject}. ${UPGRADE_GUIDE_LINK}"
    }

    /**
     * Expects a deprecation warning for implicit property access from a parent project.
     */
    void expectImplicitParentPropertyDeprecation(String propertyName, String childProject, String parentProject) {
        executer.expectDocumentedDeprecationWarning(implicitParentPropertyDeprecation(propertyName, childProject, parentProject))
    }

    /**
     * Expects a deprecation warning for explicit property access from a parent project.
     */
    void expectExplicitParentPropertyDeprecation(String callerApi, String propertyName, String childProject, String parentProject) {
        executer.expectDocumentedDeprecationWarning(explicitParentPropertyDeprecation(callerApi, propertyName, childProject, parentProject))
    }

    /**
     * Expects a deprecation warning for hasProperty() calls that resolve from a parent project.
     */
    void expectParentHasPropertyDeprecation(String propertyName, String childProject, String parentProject) {
        executer.expectDocumentedDeprecationWarning(parentHasPropertyDeprecation(propertyName, childProject, parentProject))
    }

    /**
     * Expects a deprecation warning for invoking a method from a parent project.
     */
    void expectParentMethodDeprecation(String methodName, String parentProject, String childProject) {
        executer.expectDocumentedDeprecationWarning(parentMethodDeprecation(methodName, parentProject, childProject))
    }
}
