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
package org.gradle.testing

import org.junit.rules.MethodRule

import org.junit.runners.model.Statement
import org.junit.runners.model.FrameworkMethod

import org.gradle.util.TemporaryFolder

/**
 * A JUnit rule adapter for GradleContext.
 * 
 * @see GradleContext
 */
class WithGradleContext implements MethodRule {

    Map params
    @Delegate GradleContext context

    WithGradleContext(Map params = [:]) {
        this.params = params
    }

    Statement apply(Statement base, FrameworkMethod method, Object target) {
        def tempFolder = new TemporaryFolder()
        def execStatement = new Statement() {
            void evaluate() throws Throwable {
                GradleContext.with(params + [dir: tempFolder.dir]) {
                    context = delegate
                    base.evaluate()
                    context = null
                }
            }
        }

        tempFolder.apply(execStatement, method, target)
    }

}