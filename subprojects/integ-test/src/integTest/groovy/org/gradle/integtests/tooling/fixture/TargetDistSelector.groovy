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

package org.gradle.integtests.tooling.fixture

import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

/**
 * @author: Szczepan Faber, created at: 6/25/11
 */
class TargetDistSelector implements MethodRule {

    private static ThreadLocal<String> version = new ThreadLocal<String>()

    static void select(String version) {
        TargetDistSelector.version.set(version)
    }

    static void unselect() {
        version.remove()
    }

    Statement apply(Statement base, FrameworkMethod method, Object target) {
        assert target instanceof ToolingApiSpecification

        ToolingApiSpecification spec = target
        spec.optionalTargetDist = version.get()

        return base
    }
}