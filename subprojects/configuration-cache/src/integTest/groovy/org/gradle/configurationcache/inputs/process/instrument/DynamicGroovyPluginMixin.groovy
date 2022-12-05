/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache.inputs.process.instrument

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.test.fixtures.file.TestFile

/**
 * A set of helpers to generate a Groovy plugin with provided code in buildSrc and apply it to the project under test.
 * The trait is intended to be mixed into something that extends {@link org.gradle.integtests.fixtures.AbstractIntegrationSpec}.
 */
trait DynamicGroovyPluginMixin {
    void withPluginCode(String imports, String codeUnderTest, boolean enableIndy) {
        file("buildSrc/src/main/groovy/SomePlugin.groovy") << """
            import ${Plugin.name}
            import ${Project.name}

            $imports

            class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tap {
                        $codeUnderTest
                    }
                }
            }
        """

        file("buildSrc/build.gradle") << """
        compileGroovy {
            groovyOptions.optimizationOptions.indy = $enableIndy
        }
        """

        buildScript("""
            apply plugin: SomePlugin
        """)
    }

    abstract TestFile file(Object... path)

    abstract TestFile buildScript(@GroovyBuildScriptLanguage String script)
}
