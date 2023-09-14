/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal


import org.gradle.api.attributes.Category
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.plugins.jvm.PlatformDependencyModifiers
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class PlatformDependencyModifiersTest extends Specification {
    def "copies given external dependency to select platform"() {
        def modifier = TestUtil.objectFactory().newInstance(PlatformDependencyModifiers.PlatformDependencyModifier)
        def dependency = new DefaultExternalModuleDependency("group", "name", "1.0")
        dependency.setAttributesFactory(AttributeTestUtil.attributesFactory())
        when:
        dependency = modifier.modify(dependency)
        then:
        dependency.isEndorsingStrictVersions()
        dependency.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE).toString() == "platform"
    }

    def "copies given external dependency to select enforced platform"() {
        def modifier = TestUtil.objectFactory().newInstance(PlatformDependencyModifiers.EnforcedPlatformDependencyModifier)
        def dependency = new DefaultExternalModuleDependency("group", "name", "1.0")
        dependency.setAttributesFactory(AttributeTestUtil.attributesFactory())
        when:
        dependency = modifier.modify(dependency)
        then:
        dependency.getVersionConstraint().strictVersion == "1.0"
        dependency.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE).toString() == "enforced-platform"
    }

    def "does not modify given external dependency to select platform"() {
        def modifier = TestUtil.objectFactory().newInstance(PlatformDependencyModifiers.PlatformDependencyModifier)
        def dependency = new DefaultExternalModuleDependency("group", "name", "1.0")
        dependency.setAttributesFactory(AttributeTestUtil.attributesFactory())
        when:
        modifier.modify(dependency)
        then:
        !dependency.isEndorsingStrictVersions()
        dependency.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE).toString() != "platform"
    }

    def "does not modify given external dependency to select enforced platform"() {
        def modifier = TestUtil.objectFactory().newInstance(PlatformDependencyModifiers.EnforcedPlatformDependencyModifier)
        def dependency = new DefaultExternalModuleDependency("group", "name", "1.0")
        dependency.setAttributesFactory(AttributeTestUtil.attributesFactory())
        when:
        modifier.modify(dependency)
        then:
        dependency.getVersionConstraint().requiredVersion == "1.0"
        dependency.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE).toString() != "enforced-platform"
    }
}
