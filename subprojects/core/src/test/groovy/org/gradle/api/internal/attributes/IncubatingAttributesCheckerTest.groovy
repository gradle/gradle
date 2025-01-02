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

package org.gradle.api.internal.attributes

import org.gradle.api.attributes.Category
import org.gradle.api.attributes.TestSuiteName
import org.gradle.api.attributes.Usage
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class IncubatingAttributesCheckerTest extends Specification {
    def attributeValueIsolator = AttributeTestUtil.attributeValueIsolator()
    def attributesFactory = AttributeTestUtil.attributesFactory()

    // region: Single Attribute tests
    def "attribute without any @Incubating is not reported"() {
        expect:
        !IncubatingAttributesChecker.isIncubating(Usage.USAGE_ATTRIBUTE)
    }

    def "attribute with @Incubating annotation on class is reported"() {
        expect:
        !IncubatingAttributesChecker.isIncubating(TestSuiteName.TEST_SUITE_NAME_ATTRIBUTE)
    }

    def "attribute without @Incubating annotation on class, with @Incubating on value is reported"() {
        expect:
        IncubatingAttributesChecker.isIncubating(Category.CATEGORY_ATTRIBUTE, Category.VERIFICATION)
    }

    def "attribute without @Incubating annotation on class, with @Incubating on value, specified as named object is reported"() {
        expect:
        IncubatingAttributesChecker.isIncubating(Category.CATEGORY_ATTRIBUTE, TestUtil.objectFactory().named(Category, Category.VERIFICATION))
    }

    def "attribute without @Incubating annotation on class, with @Incubating on different value is not reported"() {
        expect:
        !IncubatingAttributesChecker.isIncubating(Category.CATEGORY_ATTRIBUTE, Category.LIBRARY)
    }
    // endregion

    // region: AttributeContainer tests
    def "container with attribute which doesn't use @Incubating is not reported"() {
        def container = new DefaultMutableAttributeContainer(attributesFactory, attributeValueIsolator)
        container.attribute(Usage.USAGE_ATTRIBUTE, TestUtil.objectFactory().named(Usage, Usage.JAVA_API))

        expect:
        !IncubatingAttributesChecker.isAnyIncubating(container)
    }

    def "container with @Incubating attribute is reported"() {
        def container = new DefaultMutableAttributeContainer(attributesFactory, attributeValueIsolator)
        container.attribute(TestSuiteName.TEST_SUITE_NAME_ATTRIBUTE, TestUtil.objectFactory().named(TestSuiteName, "foo"))

        expect:
        IncubatingAttributesChecker.isAnyIncubating(container)
    }

    def "container with attribute without @Incubating annotation on class, with @Incubating on value is reported"() {
        def container = new DefaultMutableAttributeContainer(attributesFactory, attributeValueIsolator)
        container.attribute(Category.CATEGORY_ATTRIBUTE, TestUtil.objectFactory().named(Category, Category.VERIFICATION))

        expect:
        IncubatingAttributesChecker.isAnyIncubating(container)
    }

    def "container with attribute without @Incubating annotation on class, with @Incubating on different value is not reported"() {
        def container = new DefaultMutableAttributeContainer(attributesFactory, attributeValueIsolator)
        container.attribute(Category.CATEGORY_ATTRIBUTE, TestUtil.objectFactory().named(Category, Category.LIBRARY))

        expect:
        !IncubatingAttributesChecker.isAnyIncubating(container)
    }
    // endregion
}
