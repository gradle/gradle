/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.jetbrains.annotations.NotNull
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit Rule supporting the {@link ToBeFixedForIsolatedProjects} annotation.
 */
class ToBeFixedForIsolatedProjectsRule implements TestRule {

    @Override
    Statement apply(@NotNull Statement base, @NotNull Description description) {
        def annotation = description.getAnnotation(ToBeFixedForIsolatedProjects.class)
        if (GradleContextualExecuter.isNotIsolatedProjects() || annotation == null) {
            return base
        }

        return new ExpectingFailureRuleStatement(base, "Isolated Projects")
    }
}
