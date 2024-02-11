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

package org.gradle.architecture.test;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Tests that various Guava collection factory methods are not used.
 *
 * The Javadoc for these methods notes that they should be treated as deprecated.  This applies
 * only to the no-argument versions of these methods; there are legitimate uses of the other
 * overloaded versions.
 *
 * Note that some methods matching this pattern, such as {@code Sets.newConcurrentHashSet()}, are not deprecated.
 */
@AnalyzeClasses(packages = "org.gradle")
public class GuavaCollectionFactoryUsageTest {
    @ArchTest
    public static final ArchRule guava_new_list_factories_are_deprecated =
        noClasses()
            .should()
            .callMethod(com.google.common.collect.Lists.class, "newArrayList")
            .orShould()
            .callMethod(com.google.common.collect.Lists.class, "newCopyOnWriteArrayList")
            .orShould()
            .callMethod(com.google.common.collect.Lists.class, "newLinkedList")
            .because("The no-argument versions of these List creation factory methods are deprecated, see the notes on their Javadoc");

    @ArchTest
    public static final ArchRule guava_new_map_factories_are_deprecated =
        noClasses()
            .should()
            .callMethod(com.google.common.collect.Maps.class, "newConcurrentMap")
            .orShould()
            .callMethod(com.google.common.collect.Maps.class, "newHashMap")
            .orShould()
            .callMethod(com.google.common.collect.Maps.class, "newLinkedHashMap")
            .orShould()
            .callMethod(com.google.common.collect.Maps.class, "newIdentityHashMap")
            .orShould()
            .callMethod(com.google.common.collect.Maps.class, "newTreeMap")
            .because("The no-argument versions of these Map creation factory methods are deprecated, see the notes on their Javadoc");

    @ArchTest
    public static final ArchRule guava_new_set_factories_are_deprecated =
        noClasses()
            .should()
            .callMethod(com.google.common.collect.Sets.class, "newCopyOnWriteArraySet")
            .orShould()
            .callMethod(com.google.common.collect.Sets.class, "newHashSet")
            .orShould()
            .callMethod(com.google.common.collect.Sets.class, "newLinkedHashSet")
            .orShould()
            .callMethod(com.google.common.collect.Sets.class, "newTreeSet")
            .because("The no-argument versions of these Set creation factory methods are deprecated, see the notes on their Javadoc");
}
