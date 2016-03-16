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

package org.gradle.language.gosu

import org.gradle.api.tasks.compile.AbstractCompilerContinuousIntegrationTest

class GosuCompilerContinuousIntegrationTest extends AbstractCompilerContinuousIntegrationTest {
    @Override
    String getCompileTaskName() {
        return 'compileMainJarMainGosu'
    }

    @Override
    String getCompileTaskType() {
        return 'PlatformGosuCompile'
    }

    @Override
    String getSourceFileName() {
        return 'src/main/gosu/Foo.gs'
    }

    @Override
    String getInitialSourceContent() {
        return 'class Foo {}'
    }

    @Override
    String getChangedSourceContent() {
        return 'class Foo { var bar = "" }'
    }

    @Override
    String getApplyAndConfigure() {
        return """
            plugins {
                id 'jvm-component'
                id 'gosu-lang'
            }

            repositories{
                mavenCentral()
            }

            model {
                components {
                    main(JvmLibrarySpec)
                }
            }
        """
    }
}
