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

@AnalyzeClasses(packages = "org.gradle")
public class GuavaFactoryUsageTest {
    @ArchTest
    public static final ArchRule no_guava_sets_newhashset_calls =
        noClasses()
            .should()
            .callMethod(com.google.common.collect.Sets.class, "newHashSet")
            .because("We want to avoid using Guava's newHashSet method");

    @ArchTest
    public static final ArchRule no_guava_maps_newhashmap_calls =
        noClasses()
            .should()
            .callMethod(com.google.common.collect.Maps.class, "newHashMap")
            .because("We want to avoid using Guava's newHashMap method");

}
