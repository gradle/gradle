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

package org.gradle.api.internal.project

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class ParentProjectPropertyLookupIntegrationTest extends AbstractIntegrationSpec {

    TestFile grandparent = buildFile
    TestFile parent = file("parent/build.gradle")
    TestFile child = file("parent/child/build.gradle")
    TestFile childKts = file("parent/child/build.gradle.kts")

    def setup() {
        settingsFile << """
            rootProject.name = "grandparent"
            include(":parent")
            include(":parent:child")
        """
        parent.touch()
    }

    def "implicitly getting property from parent is deprecated"() {
        parent << """
            ext.value = "foo"
        """
        child << """
            println(value)
        """

        when:
        executer.expectDocumentedDeprecationWarning("Calling 'findProperty' to retrieve property from parent project has been deprecated. This will fail with an error in Gradle 10. Tried to query parent project project ':parent' for property 'value' from project ':parent:child'.")
        succeeds("help")

        then:
        outputContains("foo")
    }

    def "implicitly getting property from grandparent is deprecated"() {
        grandparent << """
            ext.value = "foo"
        """
        child << """
            println(value)
        """

        when:
        executer.expectDocumentedDeprecationWarning("Getting property from parent has been deprecated. This will fail with an error in Gradle 10.")
        succeeds("help")

        then:
        outputContains("foo")
    }

    def "implicitly invoking method on parent is deprecated"() {
        parent << """
            ${format}
        """
        child << """
            foo()
            invokeMethod("foo", new Object[] {})
            invokeMethod("foo", new Object())
        """

        when:
        executer.expectDocumentedDeprecationWarning("Getting property from parent has been deprecated. This will fail with an error in Gradle 10.")
        succeeds("help")

        then:
        outputContains("called")

        where:
        format << [
            "ext.foo = { println('called') }",
            "def foo() { println('called') }"
        ]
    }

    def "implicitly invoking method on grandparent is deprecated"() {
        grandparent << """
            ${format}
        """
        child << """
            foo()
            invokeMethod("foo", new Object[] {})
            invokeMethod("foo", new Object())
        """

        when:
        2.times { executer.expectDocumentedDeprecationWarning("Getting property from parent has been deprecated. This will fail with an error in Gradle 10.") }
        succeeds("help")

        then:
        outputContains("called")

        where:
        format << [
            "ext.foo = { println('called') }",
            "def foo() { println('called') }"
        ]
    }

    def "implicitly getting property of a grandparent through dynamic getProperty is deprecated"() {
        grandparent << """
            ext.value = "foo"
        """
        child << """
            getProperty("foo")
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Project.getProperties method has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_project_get_properties")
        succeeds("help")
    }

    def "implicitly getting property of a parent through dynamic getProperty is deprecated"() {
        parent << """
            ext.value = "foo"
        """
        child << """
            getProperty("foo")
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Project.getProperties method has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_project_get_properties")
        succeeds("help")
    }

    def "implicitly getting querying presence of grandparent property through dynamic hasProperty is deprecated"() {
        grandparent << """
            ext.value = "foo"
        """
        child << """
            hasProperty("foo")
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Project.getProperties method has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_project_get_properties")
        succeeds("help")
    }

    def "implicitly getting querying presence of a parent property through dynamic hasProperty is deprecated"() {
        parent << """
            ext.value = "foo"
        """
        child << """
            hasProperty("foo")
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Project.getProperties method has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_project_get_properties")
        succeeds("help")
    }

    def "implicitly getting property of a grandparent through static getProperty is deprecated"() {
        grandparent << """
            ext.value = "foo"
        """
        childKts << """
            getProperty("foo")
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Project.getProperties method has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_project_get_properties")
        succeeds("help")
    }

    def "implicitly getting property of a parent through static getProperty is deprecated"() {
        parent << """
            ext.value = "foo"
        """
        childKts << """
            getProperty("foo")
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Project.getProperties method has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_project_get_properties")
        succeeds("help")
    }

    def "implicitly getting querying presence of grandparent property through static hasProperty is deprecated"() {
        grandparent << """
            ext.value = "foo"
        """
        childKts << """
            hasProperty("foo")
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Project.getProperties method has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_project_get_properties")
        succeeds("help")
    }

    def "implicitly getting querying presence of a parent property through static hasProperty is deprecated"() {
        parent << """
            ext.value = "foo"
        """
        childKts << """
            hasProperty("foo")
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Project.getProperties method has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_project_get_properties")
        succeeds("help")
    }

    def "statically getting properties of a project is deprecated"() {
        buildKotlinFile << """
            properties
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Project.getProperties method has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_project_get_properties")
        succeeds("help")
    }

    def "dynamically getting properties of a project is deprecated"() {
        buildFile << """
            properties
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Dynamically calling getProperties() on a script has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_script_get_properties")
        succeeds("help")
    }

    def "dynamically getting properties of a settings script is deprecated"() {
        settingsFile << """
            properties
        """
        child.touch()

        expect:
        executer.expectDocumentedDeprecationWarning("Dynamically calling getProperties() on a script has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_script_get_properties")
        succeeds("help")
    }

    def "dynamically getting properties of init script is deprecated"() {
        file("init.gradle") << """
            properties
        """

        when:
        executer.expectDocumentedDeprecationWarning("Dynamically calling getProperties() on a script has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_script_get_properties")
        fails("help", "-I", "init.gradle")

        then:
        failure.assertHasCause("The default project is not yet available for build")
    }


    /*

    @Override
    public Object property(String propertyName) throws MissingPropertyException {
        return dynamicLookupRoutine.property(extensibleDynamicObject, propertyName);
    }

    @Override
    public Object findProperty(String propertyName) {
        return dynamicLookupRoutine.findProperty(extensibleDynamicObject, propertyName);
    }

    @Override
    public void setProperty(String name, Object value) {
        dynamicLookupRoutine.setProperty(extensibleDynamicObject, name, value);
    }

    @Override
    public boolean hasProperty(String propertyName) {
        return dynamicLookupRoutine.hasProperty(extensibleDynamicObject, propertyName);
    }

    @Override
    public Map<String, ? extends @Nullable Object> getProperties() {
        DeprecationLogger.deprecateMethod(Project.class, "getProperties")
            .willBecomeAnErrorInGradle10()
            .withUpgradeGuideSection(9, "deprecated_project_get_properties")
            .nagUser();
        return dynamicLookupRoutine.getProperties(extensibleDynamicObject);
    }
     */

}
