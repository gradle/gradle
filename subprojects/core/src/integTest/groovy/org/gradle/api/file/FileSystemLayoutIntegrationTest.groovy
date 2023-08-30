/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.file

import org.gradle.api.internal.file.DefaultFileSystemLayout
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class FileSystemLayoutIntegrationTest extends AbstractIntegrationSpec {
    def "layout is available for injection"() {
        settingsFile """
            import javax.inject.Inject
            import org.gradle.api.file.FileSystemLayout
            import org.gradle.api.provider.ProviderFactory
            import org.gradle.api.initialization.Settings
            abstract class SomePlugin implements Plugin<Settings> {
                @Inject
                abstract FileSystemLayout getLayout()

                @Inject
                abstract ProviderFactory getProviders()

                void apply(Settings s) {
                    println "settings root dir: " + layout.dir(providers.provider { s.rootDir }).get()
                    println "settings dir: " + layout.dir(providers.provider { s.settingsDir }).get()
                    println "settings source file: " + layout.dir(providers.provider { s.buildscript.sourceFile }).get()
                    println "layout implementation: " + layout.class.name
                }
            }

            apply plugin: SomePlugin
"""

        when:
        run("help")

        then:
        outputContains("settings root dir: " + testDirectory)
        outputContains("settings dir: " + testDirectory)
        outputContains("settings source file: " + settingsFile)
        outputContains("layout implementation: " + DefaultFileSystemLayout.class.name)
    }

    def "layout is available for scripts"() {
        settingsFile """
            import javax.inject.Inject
            import org.gradle.api.file.FileSystemLayout
            import org.gradle.api.provider.ProviderFactory
            import org.gradle.api.initialization.Settings

            interface Services {
                @Inject FileSystemLayout getLayout()
            }

            def layout = extensions.create("fileSystemLayout", Services).layout

            println "settings root dir: " + layout.dir(providers.provider { rootDir }).get()
            println "settings dir: " + layout.dir(providers.provider { settingsDir }).get()
            println "settings source file: " + layout.dir(providers.provider { buildscript.sourceFile }).get()
            println "layout implementation: " + layout.class.name
"""

        when:
        run("help")

        then:
        outputContains("settings root dir: " + testDirectory)
        outputContains("settings dir: " + testDirectory)
        outputContains("settings source file: " + settingsFile)
        outputContains("layout implementation: " + DefaultFileSystemLayout.class.name)
    }
}
