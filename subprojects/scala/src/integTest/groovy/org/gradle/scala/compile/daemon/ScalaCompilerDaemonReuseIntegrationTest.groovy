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

package org.gradle.scala.compile.daemon

import org.gradle.api.tasks.compile.AbstractCompilerDaemonReuseIntegrationTest
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.language.scala.fixtures.TestScalaComponent


class ScalaCompilerDaemonReuseIntegrationTest extends AbstractCompilerDaemonReuseIntegrationTest {
    @Override
    String getCompileTaskType() {
        return "ScalaCompile"
    }

    @Override
    String getApplyAndConfigure() {
        return """
            apply plugin: "scala"
            
            ${mavenCentralRepository()}
            
            dependencies {
                compile 'org.scala-lang:scala-library:2.11.12'
            }
        """
    }

    @Override
    TestJvmComponent getComponent() {
        return new TestScalaComponent()
    }
}
