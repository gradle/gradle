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

package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

class EclipseSourceSetIntegrationSpec extends AbstractEclipseIntegrationSpec {

    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    def "Source set defined on dependencies"() {
        setup:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'eclipse'

            ${jcenterRepository()}

            dependencies {
                compile 'com.google.guava:guava:18.0'
                testCompile 'junit:junit:4.12'
            }
        """

        when:
        run 'eclipse'

        then:
        EclipseClasspathFixture classpath = classpath('.')
        classpath.lib('guava-18.0.jar').assertHasAttribute('gradle_source_sets', 'main,test')
        classpath.lib('junit-4.12.jar').assertHasAttribute('gradle_source_sets', 'test')
    }

    def "Source sets defined on source folders"() {
        setup:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'eclipse'
        """
        file('src/main/java').mkdirs()
        file('src/test/java').mkdirs()

        when:
        run 'eclipse'

        then:
        EclipseClasspathFixture classpath = classpath('.')
        classpath.sourceDir('src/main/java').assertHasAttribute('gradle_source_sets', 'main')
        classpath.sourceDir('src/test/java').assertHasAttribute('gradle_source_sets', 'test')
    }

    def "Source set information is customizable in whenMerged block"() {
        setup:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'eclipse'

            ${jcenterRepository()}

            dependencies {
                compile 'com.google.guava:guava:18.0'
                testCompile 'junit:junit:4.12'
            }

            eclipse.classpath.file.whenMerged {
                def testDir = entries.find { entry -> entry.path == 'src/test/java' }
                def guavaDep = entries.find { entry -> entry.path.contains 'guava-18.0.jar' }
                testDir.entryAttributes['gradle_source_sets'] = 'test,integTest'
                guavaDep.entryAttributes['gradle_source_sets'] = 'main,test,integTest'
            }
        """
        file('src/test/java').mkdirs()

        when:
        run 'eclipse'

        then:
        EclipseClasspathFixture classpath = classpath('.')
        classpath.sourceDir('src/test/java').assertHasAttribute('gradle_source_sets', 'test,integTest')
        classpath.lib('junit-4.12.jar').assertHasAttribute('gradle_source_sets', 'test')
        classpath.lib('guava-18.0.jar').assertHasAttribute('gradle_source_sets', 'main,test,integTest')
    }
}
