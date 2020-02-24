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

package org.gradle.language.scala

import org.gradle.api.tasks.compile.AbstractComponentCompilerDaemonReuseIntegrationTest
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.language.scala.fixtures.TestScalaComponent

class ScalaComponentCompilerDaemonReuseIntegrationTest extends AbstractComponentCompilerDaemonReuseIntegrationTest {

    def setup() {
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The scala-lang plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-resources plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
    }

    @Override
    String getCompileTaskType() {
        return "PlatformScalaCompile"
    }

    @Override
    String getApplyAndConfigure() {
        return """
            apply plugin: "jvm-component"
            apply plugin: "scala-lang"

            ${mavenCentralRepository()}

            model {
                components {
                    main(JvmLibrarySpec)
                }
            }
        """
    }

    @Override
    TestJvmComponent getComponent() {
        return new TestScalaComponent()
    }
}
