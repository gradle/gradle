/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.groovy.compile.daemon

import org.gradle.api.tasks.compile.AbstractCompilerDaemonReuseIntegrationTest
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.language.groovy.fixtures.TestGroovyComponent


class GroovyCompilerDaemonReuseIntegrationTest extends AbstractCompilerDaemonReuseIntegrationTest {
    @Override
    String getCompileTaskType() {
        return "GroovyCompile"
    }

    @Override
    String getApplyAndConfigure() {
        return """
            apply plugin: "groovy"

            dependencies {
                implementation localGroovy()
            }
        """
    }

    @Override
    TestJvmComponent getComponent() {
        return new TestGroovyComponent()
    }
}
