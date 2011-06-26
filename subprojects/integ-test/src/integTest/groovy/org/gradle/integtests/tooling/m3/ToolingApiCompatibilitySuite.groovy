/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.tooling.m3

import org.gradle.integtests.tooling.fixture.TargetDistSelector
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runner.Runner
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import org.junit.runners.Suite
import org.junit.runners.Suite.SuiteClasses
import org.junit.runners.model.RunnerBuilder

/**
 * @author: Szczepan Faber, created at: 6/24/11
 */
@RunWith(Gradle10M3)
@SuiteClasses([
    ToolingApiHonorsProjectCustomizationsIntegrationTest,
    ToolingApiEclipseModelIntegrationTest,
    ToolingApiModelIntegrationTest,
    ToolingApiHonorsProjectCustomizationsIntegrationTest,
    ToolingApiBuildExecutionIntegrationTest])
class ToolingApiCompatibilitySuite {
    static class Gradle10M3 extends Suite {

        Gradle10M3(Class<?> klass, RunnerBuilder builder) {
            super(klass, builder)
        }

        protected void runChild(Runner runner, RunNotifier notifier) {
            notifier.addFirstListener(new RunListener() {
                void testStarted(Description description) {
                    TargetDistSelector.select("1.0-milestone-3")
                }
                void testFinished(Description description) {
                    TargetDistSelector.unselect()
                }
                void testFailure(Failure failure) {
                    TargetDistSelector.unselect()
                }
            })
            super.runChild(runner, notifier)
        }
    }
}