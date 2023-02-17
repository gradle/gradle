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
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import groovy.lang.Closure;
import org.gradle.api.Action;

@AnalyzeClasses(packages = "org.gradle")
public class ConfigurationCreationTest {
    @ArchTest
    public static final ArchRule configurations_are_created_with_role_based_api =
            ArchRuleDefinition.noClasses()
                    .should().callMethod("org.gradle.api.artifacts.ConfigurationContainer", "create", String.class.getName())
                    .orShould().callMethod("org.gradle.api.artifacts.ConfigurationContainer", "maybeCreate", String.class.getName())
                    .orShould().callMethod("org.gradle.api.artifacts.ConfigurationContainer", "create", String.class.getName(), Closure.class.getName())
                    .orShould().callMethod("org.gradle.api.artifacts.ConfigurationContainer", "create", String.class.getName(), Action.class.getName())
                    .because("Configurations should be created with the role-based API in org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal");
}
