/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.gradle.test.fixtures.archive.JarTestFixture
import spock.lang.Issue

@TestReproducibleArchives
class WarTaskIntegrationTest extends AbstractIntegrationSpec {

    def canCreateAWarArchiveWithNoWebXml() {
        given:
        createDir('content') {
            content1 {
                file 'file1.jsp'
            }
        }
        createDir('web-inf') {
            webinf1 {
                file 'file1.txt'
            }
        }
        createDir('meta-inf') {
            metainf1 {
                file 'file2.txt'
            }
        }
        createDir('classes') {
            org {
                gradle {
                    file 'resource.txt'
                    file 'Person.class'
                }
            }
        }
        createZip("lib.jar") {
            file "Dependency.class"
        }
        and:
        buildFile << """
            task war(type: War) {
                from 'content'
                metaInf {
                    from 'meta-inf'
                }
                webInf {
                    from 'web-inf'
                }
                classpath 'classes'
                classpath 'lib.jar'
                destinationDirectory = buildDir
                archiveFileName = 'test.war'
            }
        """

        when:
        run "war"

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertManifestPresentAndFirstEntry()
        war.assertContainsFile('META-INF/MANIFEST.MF')
        war.assertContainsFile('META-INF/metainf1/file2.txt')
        war.assertContainsFile('content1/file1.jsp')
        war.assertContainsFile('WEB-INF/lib/lib.jar')
        war.assertContainsFile('WEB-INF/classes/org/gradle/resource.txt')
        war.assertContainsFile('WEB-INF/classes/org/gradle/Person.class')
        war.assertContainsFile('WEB-INF/webinf1/file1.txt')

        war.assertFileContent('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\n\r\n')
    }

    def canCreateAWarArchiveWithWebXml() {
        given:
        def webXml = file('some.xml') << '<web/>'
        createDir('web-inf') {
            webinf1 {
                file 'file1.txt'
            }
        }
        and:
        buildFile << """
            task war(type: War) {
                webInf {
                    from 'web-inf'
                    exclude '**/*.xml'
                }
                webXml = file('some.xml')
                destinationDirectory = buildDir
                archiveFileName = 'test.war'
            }
        """

        when:
        run "war"

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertContainsFile('META-INF/MANIFEST.MF')
        war.assertContainsFile('WEB-INF/web.xml')
        war.assertContainsFile('WEB-INF/webinf1/file1.txt')

        war.assertFileContent('WEB-INF/web.xml', webXml.text)
    }

    def canAddFilesToWebInfDir() {
        given:
        createDir('web-inf') {
            webinf1 {
                file 'file1.txt'
                file 'ignore.xml'
            }
        }
        createDir('web-inf2') {
            file 'file2.txt'
        }
        and:
        buildFile << """
            task war(type: War) {
                webInf {
                    from 'web-inf'
                    exclude '**/*.xml'
                }
                webInf {
                    from 'web-inf2'
                    into 'dir2'
                    include '**/file2*'
                }
                destinationDirectory = buildDir
                archiveFileName = 'test.war'
            }
        """

        when:
        run 'war'

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertContainsFile('META-INF/MANIFEST.MF')
        war.assertContainsFile('WEB-INF/webinf1/file1.txt')
        war.assertContainsFile('WEB-INF/dir2/file2.txt')
    }

    def "exclude duplicates: webXml precedence over webInf"() {
        given:
        createDir('bad') {
            file('web.xml')
        }
        file('good.xml')

        file('bad/web.xml').text = 'bad'
        file('good.xml').text = 'good'

        buildFile << '''
        task war(type: War) {
            webInf {
                from 'bad'
            }
            webXml = file('good.xml')
            destinationDirectory = buildDir
            archiveFileName = 'test.war'
            duplicatesStrategy = 'exclude'
        }
        '''

        when:
        run "war"

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertFileContent('WEB-INF/web.xml', 'good')
    }

    def "exclude duplicates: classpath precedence over webInf"() {
        given:
        createDir('bad') {
            lib {
                file('file.txt')
            }
        }
        createDir('good') {
            file('file.txt')
        }

        file('bad/lib/file.txt').text = 'bad'
        file('good/file.txt').text = 'good'

        buildFile << '''
        task war(type: War) {
            webInf {
                from 'bad'
            }
            classpath 'good/file.txt'
            destinationDirectory = buildDir
            archiveFileName = 'test.war'
            duplicatesStrategy = 'exclude'
        }
        '''

        when:
        run "war"

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertFileContent('WEB-INF/lib/file.txt', 'good')
    }

    def "exclude duplicates: webInf over normal files"() {
        given:
        createDir('bad') {
            file('file.txt')
        }
        createDir('good') {
            file('file.txt')
        }

        file('bad/file.txt').text = 'bad'
        file('good/file.txt').text = 'good'

        buildFile << '''
        task war(type: War) {
            into('WEB-INF') {
                from 'bad'
            }
            webInf {
                from 'good'
            }
            destinationDirectory = buildDir
            archiveFileName = 'test.war'
            duplicatesStrategy = 'exclude'
        }
        '''

        when:
        run "war"

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertFileContent('WEB-INF/file.txt', 'good')
    }

    def "exclude duplicates: webXml over normal files"() {
        given:
        file('originalWebXml.xml') << 'good'
        file('some-dir/web.xml') << 'bad'

        and:
        buildFile << """
            task war(type: War) {
                duplicatesStrategy = 'exclude'
                from('some-dir') {
                    into 'WEB-INF'
                }
                webXml = file('originalWebXml.xml')
                destinationDirectory = buildDir
                archiveFileName = 'test.war'
            }
        """

        when:
        run "war"

        then:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertContainsFile('WEB-INF/web.xml')
        war.assertFileContent('WEB-INF/web.xml', 'good')
    }

    @Issue("GRADLE-3522")
    @ToBeFixedForConfigurationCache(because = "early dependency resolution")
    def "war task doesn't trigger dependency resolution early"() {
        when:
        buildFile << """
configurations {
    conf
}

task assertUnresolved {
    doLast {
        assert configurations.conf.state == Configuration.State.UNRESOLVED
    }
}

task war(type: War) {
    dependsOn assertUnresolved
    classpath = configurations.conf
    destinationDirectory = buildDir
}
"""

        then:
        succeeds "war"
    }

    def "can make war task cacheable with runtime api"() {
        given:
        def webXml = file('web.xml') << '<web/>'
        createDir('web-inf') {
            webinf1 {
                file 'file1.txt'
            }
        }
        and:
        settingsFile << """
            buildCache {
                local {
                    directory = file("local-build-cache")
                }
            }
        """
        buildFile << """
            apply plugin: "base"

            task war(type: War) {
                webInf {
                    from 'web-inf'
                }
                webXml = file('web.xml')
                destinationDirectory = buildDir
                archiveFileName = 'test.war'
                outputs.cacheIf { true }
            }
        """

        when:
        withBuildCache().run "clean", "war"

        then:
        executedAndNotSkipped ':war'

        and:
        def war = new JarTestFixture(file('build/test.war'))
        war.assertContainsFile('META-INF/MANIFEST.MF')
        war.assertContainsFile('WEB-INF/web.xml')
        war.assertContainsFile('WEB-INF/webinf1/file1.txt')

        war.assertFileContent('WEB-INF/web.xml', webXml.text)

        when:
        withBuildCache().run "clean", "war"

        then:
        skipped ":war"
    }

    def "emits deprecation message when war convention is accessed"() {
        setup:
        buildFile << '''
            plugins {
                id 'war'
            }

            tasks.register('custom') {
                println webAppDir
            }
        '''

        expect:
        executer
            .expectDocumentedDeprecationWarning('The org.gradle.api.plugins.WarPluginConvention type has been deprecated. ' +
                'This is scheduled to be removed in Gradle 9.0. ' +
                'Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#war_convention_deprecation')

        succeeds 'custom'
    }
}
