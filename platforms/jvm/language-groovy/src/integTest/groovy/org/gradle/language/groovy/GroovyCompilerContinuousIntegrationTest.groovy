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

package org.gradle.language.groovy

import org.gradle.api.tasks.compile.AbstractCompilerContinuousIntegrationTest


class GroovyCompilerContinuousIntegrationTest extends AbstractCompilerContinuousIntegrationTest {
    @Override
    String getCompileTaskName() {
        return "compileGroovy"
    }

    @Override
    String getCompileTaskType() {
        return "GroovyCompile"
    }

    @Override
    String getSourceFileName() {
        return "src/main/groovy/Foo.groovy"
    }

    @Override
    String getInitialSourceContent() {
        return "class Foo {}"
    }

    @Override
    String getChangedSourceContent() {
        return "class Foo { def bar }"
    }

    @Override
    String getApplyAndConfigure() {
        return """
            apply plugin: "groovy"

            dependencies {
                implementation localGroovy()
            }

            tasks.withType(${compileTaskType}) {
                groovyOptions.fork = true
            }
        """
    }
}
