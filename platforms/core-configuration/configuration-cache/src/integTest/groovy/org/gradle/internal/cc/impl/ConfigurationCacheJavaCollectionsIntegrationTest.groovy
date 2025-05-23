/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl


import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

class ConfigurationCacheJavaCollectionsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Issue('https://github.com/gradle/gradle/issues/26942')
    @Requires(value = UnitTestPreconditions.Jdk11OrLater)
    def 'restores Java 11 collections'() {
        given:
        buildFile '''
            import java.util.*;

            class CollectionsTask extends DefaultTask {
                private List<String> list = List.of('foo', 'bar')
                private Set<String> set = Set.of('foo', 'bar')
                private Map<String, String> map = Map.of('foo', 'bar')

                @TaskAction def print() {
                    [list: list, set: set.sort(), map: map].each {
                        println it
                    }
                }
            }

            tasks.register('collections', CollectionsTask)
        '''

        when:
        configurationCacheRun 'collections'

        then:
        outputContains 'list=[foo, bar]'
        outputContains 'set=[bar, foo]'
        outputContains 'map={foo=bar}'
    }
}
