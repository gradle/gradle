/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.platform.base

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.language.base.ProjectSourceSet
import org.gradle.model.ModelMap
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.registry.RuleContext
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import spock.lang.Specification

abstract class PlatformBaseSpecification extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider(getClass())

    final def project = TestUtil.create(testDir).rootProject()
    @Rule SetRuleContext setContext = new SetRuleContext()

    def realize(String name) {
        project.modelRegistry.find(name, ModelType.UNTYPED)
    }

    ModelMap<Task> realizeTasks() {
        project.modelRegistry.find("tasks", ModelTypes.modelMap(Task))
    }

    ComponentSpecContainer realizeComponents() {
        project.modelRegistry.find("components", ComponentSpecContainer)
    }

    ProjectSourceSet realizeSourceSets() {
        project.modelRegistry.find("sources", ProjectSourceSet)
    }

    BinaryContainer realizeBinaries() {
        def binaries = project.modelRegistry.find("binaries", BinaryContainer)
        // Currently some rules take the task container as subject but actually mutate the binaries
        realizeTasks()
        return binaries
    }

    def dsl(@DelegatesTo(Project) Closure closure) {
        closure.delegate = project
        closure()
        project.bindAllModelRules()
    }

    static class SetRuleContext implements TestRule {
        @Override
        Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                void evaluate() throws Throwable {
                    RuleContext.run(new SimpleModelRuleDescriptor(description.displayName)) {
                        base.evaluate()
                    }
                }
            }
        }
    }
}
