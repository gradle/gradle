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

package org.gradle.api.internal.tasks.testing.detection.distribution;

import org.gradle.api.internal.tasks.testing.TestDefinition;
import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.TestIdentifierTestDefinition;
import org.jspecify.annotations.NullMarked;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Distributes each discovered test individually with no class-level grouping.
 * <p>
 * Every leaf test identifier (where {@link TestIdentifier#isTest()} is {@code true}) and
 * every leaf container (a container with no children, such as {@code @ParameterizedTest}
 * or {@code @TestFactory}) is sent to the processor as a separate unit of work.
 * <p>
 * This does <em>not</em> preserve class cohesion — methods from the same test class may
 * be distributed to different workers when {@code maxParallelForks > 1}. This will break
 * tests that rely on {@code @BeforeAll}/{@code @AfterAll}, {@code @Stepwise}, shared
 * static state, or any other class-level coordination.
 */
@NullMarked
public final class ByIndividualTestTestDistributor implements TestDistributor {
    @Override
    public List<List<TestDefinition>> distribute(TestPlan testPlan, TestDefinitionProcessor<TestDefinition> processor) {
        List<List<TestDefinition>> result = new ArrayList<>();

        testPlan.accept(new TestPlan.Visitor() {
            @Override
            public void visit(TestIdentifier testIdentifier) {
                if (testIdentifier.isTest() || isLeafContainer(testPlan, testIdentifier)) {
                    TestIdentifierTestDefinition definition = new TestIdentifierTestDefinition(testIdentifier);
                    processor.processTestDefinition(definition);
                    result.add(Collections.singletonList(definition));
                }
            }
        });

        return result;
    }

    private static boolean isLeafContainer(TestPlan testPlan, TestIdentifier testIdentifier) {
        return !testIdentifier.isTest() && testPlan.getChildren(testIdentifier).isEmpty();
    }
}
