/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.Finalize;
import org.gradle.model.Model;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.testing.base.TestSuiteBinarySpec;
import org.gradle.testing.base.TestSuiteContainer;
import org.gradle.testing.base.TestSuiteSpec;
import org.gradle.testing.base.TestSuiteTaskCollection;
import org.gradle.testing.base.internal.BaseTestSuiteSpec;

/**
 * Base plugin for testing.
 *
 * - Adds a {@link org.gradle.testing.base.TestSuiteContainer} named {@code testSuites} to the model.
 * - Copies test binaries from {@code testSuites} into {@code binaries}.
 */
@Incubating
public class TestingModelBasePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);
    }

    static class Rules extends RuleSource {
        @ComponentType
        void registerTestSuiteSpec(TypeBuilder<TestSuiteSpec> builder) {
            builder.defaultImplementation(BaseTestSuiteSpec.class);
        }

        @Model
        void testSuites(TestSuiteContainer testSuites) {
        }

        @Mutate
        void copyTestBinariesToGlobalContainer(BinaryContainer binaries, TestSuiteContainer testSuites) {
            for (TestSuiteSpec testSuite : testSuites.values()) {
                for (BinarySpecInternal binary : testSuite.getBinaries().withType(BinarySpecInternal.class).values()) {
                    binaries.put(binary.getProjectScopedName(), binary);
                }
            }
        }

        @Finalize
        void linkTestSuiteBinariesRunTaskToBinariesCheckTasks(@Path("binaries") ModelMap<TestSuiteBinarySpec> binaries) {
            binaries.afterEach(new Action<TestSuiteBinarySpec>() {
                @Override
                public void execute(TestSuiteBinarySpec testSuiteBinary) {
                    if (testSuiteBinary.isBuildable()) {
                        if (testSuiteBinary.getTasks() instanceof TestSuiteTaskCollection) {
                            testSuiteBinary.checkedBy(((TestSuiteTaskCollection) testSuiteBinary.getTasks()).getRun());
                        }
                        BinarySpec testedBinary = testSuiteBinary.getTestedBinary();
                        if (testedBinary != null && testedBinary.isBuildable()) {
                            testedBinary.checkedBy(testSuiteBinary.getCheckTask());
                        }
                    }
                }
            });
        }

        @Finalize
        void attachBinariesCheckTasksToCheckLifecycle(@Path("tasks.check") Task checkTask, @Path("binaries") ModelMap<BinarySpec> binaries) {
            for (BinarySpec binary : binaries) {
                if (binary.isBuildable()) {
                    Task binaryCheckTask = binary.getCheckTask();
                    if (binaryCheckTask != null) {
                        checkTask.dependsOn(binaryCheckTask);
                    }
                }
            }
        }
    }
}
