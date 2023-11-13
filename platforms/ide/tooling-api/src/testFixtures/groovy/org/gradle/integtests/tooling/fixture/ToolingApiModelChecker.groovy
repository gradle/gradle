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

package org.gradle.integtests.tooling.fixture

import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.GradleTask
import org.gradle.tooling.model.ProjectIdentifier

/**
 * Fixture to structurally match models returned by the Tooling API.
 */
class ToolingApiModelChecker {

    static <T> void checkModel(T actual, T expected, List<Object> specs) {
        assert (actual == null) == (expected == null)
        if (expected == null) {
            return
        }

        if (expected instanceof DomainObjectSet) {
            assert actual instanceof DomainObjectSet
            checkDomainObjectSet(actual, expected) { actualItem, expectedItem ->
                checkModel(actualItem, expectedItem, specs)
            }
            return
        }

        for (def spec in specs) {
            checkModelProperty(actual, expected, spec)
        }
    }

    static void checkDomainObjectSet(DomainObjectSet<?> actual, DomainObjectSet<?> expected, Closure checker) {
        actual.size() == expected.size()
        [actual, expected].collect { it.all }
            .transpose()
            .each { actualItem, expectedItem ->
                checker(actualItem, expectedItem)
            }
    }

    static <T> void checkModelProperty(T actual, T expected, def spec) {
        if (spec instanceof Closure) {
            def getter = spec as Closure
            assert getter(actual) == getter(expected)
        } else if (spec instanceof List) {
            assert spec.size() == 2, "spec in the form of a list must have 2 items: a getter and a sub-spec"
            def getter = spec[0] as Closure
            def checker = spec[1]
            def actualValue = getter(actual)
            def expectedValue = getter(expected)
            if (checker instanceof Closure) {
                if (expectedValue instanceof DomainObjectSet) {
                    assert actualValue instanceof DomainObjectSet
                    checkDomainObjectSet(actualValue, expectedValue, checker)
                }
            } else if (checker instanceof List) {
                def subSpecs = checker as List
                checkModel(actualValue, expectedValue, subSpecs)
            } else {
                throw new IllegalStateException("Unexpected type of the checker: ${checker.getClass()}")
            }
        } else {
            throw new IllegalStateException("Unexpected type of the spec: ${spec.getClass()}")
        }
    }

    static void checkProjectIdentifier(actual, expected) {
        assert expected instanceof ProjectIdentifier
        assert actual instanceof ProjectIdentifier

        checkModel(actual, expected, [
            { it.projectPath },
            { it.buildIdentifier.rootDir },
        ])
    }

    static void checkGradleTask(actual, expected) {
        assert expected instanceof GradleTask
        assert actual instanceof GradleTask

        checkModel(actual, expected, [
            { it.path },
            { it.buildTreePath },
            { it.name },
            { it.description },
            { it.group },
            { it.isPublic() },
            [{ it.projectIdentifier }, { a, e -> checkProjectIdentifier(a, e) }],
            { it.project.path }, // only check path to avoid infinite recursion
        ])
    }

    static void checkGradleProject(actual, expected) {
        assert expected instanceof GradleProject
        assert actual instanceof GradleProject

        checkModel(actual, expected, [
            { it.name },
            { it.description },
            [{ it.projectIdentifier }, { a, e -> checkProjectIdentifier(a, e) }],
            { it.path },
            { it.buildScript.sourceFile },
            { it.buildDirectory },
            { it.projectDirectory },
            [{ it.tasks }, { a, e -> checkGradleTask(a, e) }],
            { it.parent?.path },
            [{ it.children }, { a, e -> checkGradleProject(a, e) }],
        ])
    }

}
