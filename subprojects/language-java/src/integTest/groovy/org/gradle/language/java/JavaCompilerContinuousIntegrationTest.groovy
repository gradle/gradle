/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.java

import org.gradle.api.tasks.compile.AbstractCompilerContinuousIntegrationTest
import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution

@UnsupportedWithInstantExecution(because = "software model")
class JavaCompilerContinuousIntegrationTest extends AbstractCompilerContinuousIntegrationTest {

    @Override
    String getCompileTaskType() {
        return "PlatformJavaCompile"
    }

    @Override
    String getCompileTaskName() {
        return "compileMainJarMainJava"
    }

    @Override
    String getSourceFileName() {
        return "src/main/java/Foo.java"
    }

    @Override
    String getInitialSourceContent() {
        return "public class Foo {}"
    }

    @Override
    String getChangedSourceContent() {
        return "public class Foo { String bar; }"
    }

    @Override
    String getApplyAndConfigure() {
        return """
            plugins {
                id 'jvm-component'
                id 'java-lang'
            }

            model {
                components {
                    main(JvmLibrarySpec)
                }
            }

            tasks.withType(${compileTaskType}) {
                options.fork = true
            }
        """
    }
}
