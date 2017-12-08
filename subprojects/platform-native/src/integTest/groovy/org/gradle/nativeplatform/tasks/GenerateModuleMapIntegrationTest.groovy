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

package org.gradle.nativeplatform.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.util.TextUtil.normaliseLineSeparators

class GenerateModuleMapIntegrationTest extends AbstractIntegrationSpec {

    def "generates a simple module map file"() {
        buildFile << """
            import ${GenerateModuleMap.class.canonicalName}

            task generate(type: GenerateModuleMap) {
                moduleName.set('foo') 
                moduleMapFile.set(project.layout.buildDirectory.file('moduleMaps/module.modulemap'))
                publicHeaderDirs.add(file('headers'))
                publicHeaderDirs.add(file('moreHeaders'))
                publicHeaderDirs.add(file('does-not-exist'))
            }
        """
        file('headers').createDir()
        file('moreHeaders').createDir()

        expect:
        succeeds "generate"
        def moduleMapFile = file('build/moduleMaps/module.modulemap')
        moduleMapFile.exists()
        normaliseLineSeparators(moduleMapFile.text) == content('foo', 'headers', 'moreHeaders')
    }

    def "generating a module map file is incremental"() {
        def moduleMapFile = file('build/moduleMaps/module.modulemap')
        buildFile << """
            import ${GenerateModuleMap.class.canonicalName}

            task generate(type: GenerateModuleMap) {
                moduleName.set('foo') 
                moduleMapFile.set(project.layout.buildDirectory.file('moduleMaps/module.modulemap'))
                publicHeaderDirs.add(file('headers'))
                publicHeaderDirs.add(file('moreHeaders'))
            }
        """
        file('headers').createDir()
        file('moreHeaders').createDir()

        when:
        succeeds "generate"

        then:
        executedAndNotSkipped ":generate"
        moduleMapFile.exists()
        normaliseLineSeparators(moduleMapFile.text) == content('foo', 'headers', 'moreHeaders')

        when:
        succeeds "generate"

        then:
        skipped ":generate"

        when:
        buildFile << """
            generate.moduleName.set('bar')
        """
        succeeds "generate"

        then:
        executedAndNotSkipped ":generate"
        normaliseLineSeparators(moduleMapFile.text) == content('bar', 'headers', 'moreHeaders')

        when:
        buildFile << """
            generate.publicHeaderDirs.add(file('yet/more/headers'))
        """
        file('yet/more/headers').createDir()
        succeeds "generate"

        then:
        executedAndNotSkipped ":generate"
        normaliseLineSeparators(moduleMapFile.text) == content('bar', 'headers', 'moreHeaders', 'yet/more/headers')
    }

    String content(String moduleName, String... headerPaths) {
        return """module ${moduleName} {
${headerPaths.collect { "\tumbrella \"${file(it).absolutePath}\"" }.join("\n")}
\texport *
}
"""
    }
}
