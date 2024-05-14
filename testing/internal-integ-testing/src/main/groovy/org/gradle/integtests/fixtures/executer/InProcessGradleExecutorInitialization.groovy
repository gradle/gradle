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

package org.gradle.integtests.fixtures.executer

import org.gradle.api.model.ObjectFactory
import org.spockframework.runtime.extension.IGlobalExtension
import spock.lang.Issue

/**
 * Initializes services for the in-process Gradle executor.
 * <p>
 * If you don't initialize the services, some global state may end up with the wrong kind of values.
 * For example, {@link org.gradle.internal.instantiation.generator.AsmBackedClassGenerator}.GENERATED_CLASSES_CACHES
 * may have a test implementation instead of the real thing when a unit test runs first.
 */
@Issue("https://github.com/gradle/gradle-private/issues/3534")
class InProcessGradleExecutorInitialization implements IGlobalExtension {
    @Override
    void start() {
        if (GradleContextualExecuter.embedded ) {
            AbstractGradleExecuter.GLOBAL_SERVICES.get(ObjectFactory)
        }
    }
}
