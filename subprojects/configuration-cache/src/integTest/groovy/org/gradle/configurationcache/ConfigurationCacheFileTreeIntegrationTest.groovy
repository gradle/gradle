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

package org.gradle.configurationcache


import spock.lang.Issue

class ConfigurationCacheFileTreeIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Issue('https://github.com/gradle/gradle/issues/19780')
    def "filter predicates and matching patterns are evaluated lazily"() {
        given:
        buildFile """
            abstract class PrintInputs extends DefaultTask {

                @InputFiles
                abstract ConfigurableFileCollection getInputFiles()

                @TaskAction
                def printInputs() {
                    getInputFiles().files.each {
                        println('*' + it.name + '*')
                    }
                }
            }

            tasks.register('ok', PrintInputs) {
                def patternSet = new org.gradle.api.tasks.util.PatternSet().include('**/*.txt')
                inputFiles.from(
                    fileTree(dir: 'src', include: '**/*.*')
                        .$pattern
                )
            }
        """
        createDir('src') {
            file('foo.txt').write('')
        }

        and:
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun 'ok'

        then:
        outputContains '*foo.txt*'

        when: 'a new input appears!'
        file('src/bar.txt').write('')

        and:
        configurationCacheRun 'ok'

        then:
        outputContains '*bar.txt*'

        and:
        configurationCache.assertStateLoaded()

        where:
        pattern                                              | _
        'filter { it.file }'                                 | _
        'matching(patternSet)'                               | _
        'matching(patternSet).filter { it.file }'            | _
        'matching(patternSet).asFileTree.filter { it.file }' | _
        'filter { it.file }.asFileTree'                      | _
        'filter { it.file }.asFileTree.matching(patternSet)' | _
    }
}
