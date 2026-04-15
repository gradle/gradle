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
 * Distributes tests at top-level container granularity, preserving cohesion within each container.
 * <p>
 * Each top-level container (test class, resource file, or directory) is sent to the processor
 * as a single unit of work, ensuring all tests within it execute together in the same worker.
 * This preserves correctness for:
 * <ul>
 *   <li>{@code @BeforeAll} / {@code @AfterAll} lifecycle methods</li>
 *   <li>{@code @Nested} class hierarchies sharing outer class state</li>
 *   <li>{@code @Stepwise} (Spock) ordered execution</li>
 *   <li>Shared mutable static state across methods</li>
 *   <li>{@code @TestFactory}, {@code @ParameterizedTest}, {@code @RepeatedTest} — test
 *       templates that expand at execution time</li>
 *   <li>Resource-based test files discovered by custom test engines</li>
 * </ul>
 * <p>
 * A top-level container is a {@link TestIdentifier} that is a direct child of an engine root.
 * Engine roots have no {@code TestSource}, so a top-level container is any identifier whose
 * parent has no source. This covers class-based containers ({@code ClassSource}),
 * file-based containers ({@code FileSource}), directory-based containers ({@code DirectorySource}),
 * and any other source type from custom engines.
 */
@NullMarked
public final class ByTopLevelContainerTestDistributor implements TestDistributor {
    @Override
    public List<List<TestDefinition>> distribute(TestPlan testPlan, TestDefinitionProcessor<TestDefinition> processor) {
        List<TestIdentifier> topLevelContainers = collectTopLevelContainers(testPlan);

        List<List<TestDefinition>> result = new ArrayList<>();
        for (TestIdentifier container : topLevelContainers) {
            TestIdentifierTestDefinition definition = new TestIdentifierTestDefinition(container);
            processor.processTestDefinition(definition);
            result.add(Collections.singletonList(definition));
        }
        return result;
    }

    private static List<TestIdentifier> collectTopLevelContainers(TestPlan testPlan) {
        List<TestIdentifier> topLevelContainers = new ArrayList<>();
        testPlan.accept(new TestPlan.Visitor() {
            @Override
            public void visit(TestIdentifier testIdentifier) {
                if (isTopLevelContainer(testPlan, testIdentifier)) {
                    topLevelContainers.add(testIdentifier);
                }
            }
        });
        return topLevelContainers;
    }

    /**
     * Determines whether the given identifier is a top-level container.
     * <p>
     * A top-level container is a direct child of an engine root. Engine roots have no
     * {@code TestSource}, so this checks that the identifier has a source and its parent
     * does not. This distinguishes top-level test classes and resource files from
     * {@code @Nested} inner classes and individual test methods.
     *
     * @param testPlan the test plan providing the parent hierarchy
     * @param testIdentifier the identifier to check
     * @return {@code true} if this is a top-level container
     */
    static boolean isTopLevelContainer(TestPlan testPlan, TestIdentifier testIdentifier) {
        if (!testIdentifier.getSource().isPresent()) {
            return false;
        }
        return testPlan.getParent(testIdentifier)
            .map(parent -> !parent.getSource().isPresent())
            .orElse(false);
    }
}
