/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.base.plugins;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.TestSuiteName;
import org.gradle.api.attributes.VerificationType;
import org.gradle.api.model.ObjectFactory;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;
import org.gradle.testing.base.internal.DefaultTestingExtension;

/**
 * Base test suite functionality. Makes an extension named "testing" available to the project.
 *
 * @since 7.3
 */
@Incubating
public abstract class TestSuiteBasePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        TestingExtension testing = project.getExtensions().create(TestingExtension.class, "testing", DefaultTestingExtension.class);

        testing.getSuites().configureEach(suite -> {
            // TODO: Eventually, we want a test results variant for each target, but cannot do so now because:
            // 1. Targets need a way to uniquely identify themselves via attributes. We do not have an API to describe
            //    a target using attributes yet.
            // 2. If a suite has multiple test results variants, we get ambiguity when resolving the test results variant.
            //    We should add a feature to dependency management allowing ArtifactView to select multiple variants from the target component.
            NamedDomainObjectProvider<ConsumableConfiguration> testResultsVariant = addTestResultsVariant(project, suite);

            suite.getTargets().configureEach(target -> {
                testResultsVariant.configure(variant -> {
                    variant.getOutgoing().artifact(
                        target.getBinaryResultsDirectory(),
                        artifact -> artifact.setType(ArtifactTypeDefinition.DIRECTORY_TYPE)
                    );
                });
            });
        });
    }

    private static NamedDomainObjectProvider<ConsumableConfiguration> addTestResultsVariant(Project project, TestSuite suite) {
        String variantName = String.format("testResultsElementsFor%s", StringUtils.capitalize(suite.getName()));

        return project.getConfigurations().consumable(variantName, conf -> {
            conf.setDescription("Binary results obtained from running all targets in the '" + suite.getName() + "' Test Suite.");

            ObjectFactory objects = project.getObjects();
            conf.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.VERIFICATION));
                attributes.attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.class, VerificationType.TEST_RESULTS));

                // TODO: Allow targets to define attributes uniquely identifying themselves.
                // Then, create a test results variant for each target instead of each suite.
                attributes.attribute(TestSuiteName.TEST_SUITE_NAME_ATTRIBUTE, objects.named(TestSuiteName.class, suite.getName()));
            });
        });
    }
}
