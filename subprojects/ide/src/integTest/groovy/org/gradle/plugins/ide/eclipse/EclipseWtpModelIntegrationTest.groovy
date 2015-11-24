/*
 * Copyright 2010 the original author or authors.
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

import spock.lang.Issue

class EclipseWtpModelIntegrationTest extends AbstractEclipseIntegrationSpec {
    def "allows configuring Eclipse wtp"() {
        given:
        file('someExtraSourceDir').mkdirs()
        file('src/foo/bar').mkdirs()

        mavenRepo.module("gradle", "foo").publish()
        mavenRepo.module("gradle", "bar").publish()
        mavenRepo.module("gradle", "baz").publish()

        buildFile << """
            apply plugin: 'java'
            apply plugin: 'war'
            apply plugin: 'eclipse-wtp'

            configurations {
              configOne
              configTwo
            }

            repositories {
              maven { url "${mavenRepo.uri}" }
            }

            dependencies {
              configOne 'gradle:foo:1.0', 'gradle:bar:1.0', 'gradle:baz:1.0'
              configTwo 'gradle:baz:1.0'
            }

            eclipse {

              pathVariables 'userHomeVariable' : file(System.properties['user.home'])

              wtp {
                component {
                  contextPath = 'killerApp'

                  sourceDirs += file('someExtraSourceDir')

                  plusConfigurations << configurations.configOne
                  minusConfigurations << configurations.configTwo

                  deployName = 'someBetterDeployName'

                  resource sourcePath: './src/foo/bar', deployPath: './deploy/foo/bar'

                  property name: 'wbPropertyOne', value: 'New York!'
                }
                facet {
                  facet name: 'gradleFacet', version: '1.333'
                }
              }
            }
        """

        when:
        succeeds('eclipse')
        def component = componentFile.text
        def facet = facetFile.text

        then:
        component.contains('someExtraSourceDir')
        component.contains('foo-1.0.jar')
        component.contains('bar-1.0.jar')
        !component.contains('baz-1.0.jar')
        component.contains('someBetterDeployName')

        //contains('userHomeVariable') //TODO don't know how to test it at the moment

        component.contains('./src/foo/bar')
        component.contains('./deploy/foo/bar')
        component.contains('wbPropertyOne')
        component.contains('New York!')
        component.contains('killerApp')

        facet.contains('gradleFacet')
        facet.contains('1.333')
    }

    @Issue("GRADLE-2653")
    def "wtp component respects configuration modifications"() {
        given:
        mavenRepo.module("gradle", "foo").publish()
        mavenRepo.module("gradle", "bar").publish()
        mavenRepo.module("gradle", "baz").publish()
        mavenRepo.module("gradle", "baz", "2.0").publish()

        buildFile << """
            apply plugin: 'java'
            apply plugin: 'war'
            apply plugin: 'eclipse-wtp'

            repositories {
              maven { url "${mavenRepo.uri}" }
            }

            dependencies {
              compile 'gradle:foo:1.0', 'gradle:bar:1.0', 'gradle:baz:1.0'
            }

            configurations.all {
              exclude module: 'bar' //an exclusion
              resolutionStrategy.force 'gradle:baz:2.0' //forced module
            }
        """

        when:
        succeeds('eclipse')
        def component =  componentFile.text

        then:
        component.contains('foo-1.0.jar')
        component.contains('baz-2.0.jar') //forced version
        !component.contains('bar') //excluded
    }


    def "allows configuring hooks for component"() {
        given:
        componentFile << '''<?xml version="1.0" encoding="UTF-8"?>
            <project-modules id="moduleCoreId" project-version="2.0">
              <wb-module deploy-name="coolDeployName">
                <property name="context-root" value="root"/>
                <wb-resource deploy-path="/" source-path="src/main/webapp"/>
              </wb-module>
            </project-modules>
        '''

        buildFile << """
            apply plugin: 'java'
            apply plugin: 'war'
            apply plugin: 'eclipse-wtp'

            def hooks = []

            eclipse {
              wtp {
                component {
                  file {
                    beforeMerged {
                      hooks << 'beforeMerged'
                      assert it.deployName == 'coolDeployName'
                    }
                    whenMerged {
                      hooks << 'whenMerged'
                      it.deployName = 'betterDeployName'
                    }
                    withXml { it.asNode().appendNode('be', 'cool') }
                  }
                }
              }
            }

            eclipseWtpComponent.doLast() {
              assert hooks == ['beforeMerged', 'whenMerged']
            }
        """

        when:
        succeeds('eclipse')
        def component = componentFile.text

        then:
        component.contains('betterDeployName')
        !component.contains('coolDeployName')
        component.contains('<be>cool</be>')
    }


    def "allows configuring hooks for facet"() {
        given:
        facetFile << '''<?xml version="1.0" encoding="UTF-8"?>
            <faceted-project>
              <fixed facet="jst.java"/>
              <fixed facet="jst.web"/>
              <installed facet="jst.web" version="2.4"/>
              <installed facet="jst.java" version="5.0"/>
              <installed facet="facet.one" version="1.0"/>
            </faceted-project>
        '''

        buildFile << """
            import org.gradle.plugins.ide.eclipse.model.Facet

            apply plugin: 'java'
            apply plugin: 'war'
            apply plugin: 'eclipse-wtp'

            eclipse {
              wtp {
                facet {
                  file {
                    beforeMerged {
                      assert it.facets.contains(new Facet('facet.one', '1.0'))
                      it.facets.add(new Facet('facet.two', '2.0'))
                    }
                    whenMerged {
                      assert it.facets.contains(new Facet('facet.one', '1.0'))
                      assert it.facets.contains(new Facet('facet.two', '2.0'))
                      it.facets.add(new Facet('facet.three', '3.0'))
                    }
                    withXml { it.asNode().appendNode('be', 'cool') }
                  }
                }
              }
            }
        """

        when:
        succeeds('eclipse')
        def facet = facetFile.text

        then:
        facet.contains('facet.one')
        facet.contains('facet.two')
        facet.contains('facet.three')
        facet.contains('<be>cool</be>')
    }

    @Issue("GRADLE-2661")
    def "file dependencies respect plus minus configurations"() {
        given:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'war'
            apply plugin: 'eclipse-wtp'

            configurations {
              configOne
              configTwo
            }

            dependencies {
              configOne files('foo.txt', 'bar.txt', 'baz.txt')
              configTwo files('baz.txt')
            }

            eclipse {
              wtp {
                component {
                  plusConfigurations << configurations.configOne
                  minusConfigurations << configurations.configTwo
                }
              }
            }
        """

        when:
        succeeds('eclipse')
        def component = componentFile.text

        then:
        component.contains('foo.txt')
        component.contains('bar.txt')
        !component.contains('baz.txt')
    }


    @Issue("GRADLE-1881")
    def "uses eclipse project name for wtp module dependencies"() {
        given:
        settingsFile << "include 'impl', 'contrib'"
        buildFile << """
            project(':impl') {
              apply plugin: 'java'
              apply plugin: 'war'
              apply plugin: 'eclipse-wtp'

              dependencies { compile project(':contrib') }

              eclipse.project.name = 'cool-impl'
            }

            project(':contrib') {
              apply plugin: 'java'
              apply plugin: 'eclipse-wtp'
              //should not have war nor ear applied

              eclipse.project.name = 'cool-contrib'
            }
        """

        when:
        succeeds('eclipse')
        def implComponent = wtpComponent('impl')
        def contribComponent = wtpComponent('contrib')

        then:
        implComponent.deployName == 'cool-impl'
        implComponent.project('cool-contrib')
        contribComponent.deployName == 'cool-contrib'
    }

    @Issue("GRADLE-1881")
    def "does not explode if dependent project does not have eclipse plugin"() {
        given:
        settingsFile << "include 'impl', 'contrib'"

        buildFile << """
            project(':impl') {
              apply plugin: 'java'
              apply plugin: 'war'
              apply plugin: 'eclipse-wtp'

              dependencies { compile project(':contrib') }

              eclipse.project.name = 'cool-impl'
            }

            project(':contrib') {
              apply plugin: 'java'
            }
        """

        when:
        succeeds('eclipse')

        then:
        notThrown(Exception)
    }

    @Issue("GRADLE-2030")
    def "component for war plugin does not contain non-existing source and resource dirs"() {
        given:
        file('xxxSource').createDir()
        file('xxxResource').createDir()

        buildFile << """
            apply plugin: 'java'
            apply plugin: 'war'
            apply plugin: 'eclipse-wtp'

            sourceSets.main.java.srcDirs 'yyySource', 'xxxSource'

            eclipse.wtp.component {
              resource sourcePath: 'xxxResource', deployPath: 'deploy-xxx'
              resource sourcePath: 'yyyResource', deployPath: 'deploy-yyy'
            }
        """

        when:
        succeeds('eclipse')
        def component = componentFile.text

        then:
        component.contains('xxxSource')
        !component.contains('yyySource')

        component.contains('xxxResource')
        !component.contains('yyyResource')
    }

    @Issue("GRADLE-2030")
    def "component for ear plugin does not contain non-existing source and resource dirs"() {
        given:
        file('xxxSource').createDir()
        file('xxxResource').createDir()

        buildFile << """
          apply plugin: 'java'
          apply plugin: 'ear'
          apply plugin: 'eclipse-wtp'

          sourceSets.main.java.srcDirs 'yyySource', 'xxxSource'

          appDirName = 'nonExistingAppDir'

          eclipse.wtp.component {
            resource sourcePath: 'xxxResource', deployPath: 'deploy-xxx'
            resource sourcePath: 'yyyResource', deployPath: 'deploy-yyy'
          }
        """

        when:
        succeeds('eclipse')
        def component = getComponentFile().text

        then:
        component.contains('xxxSource')
        !component.contains('yyySource')

        component.contains('xxxResource')
        !component.contains('yyyResource')

        !component.contains('nonExistingAppDir')
    }

    def "component for ear plugin contains the app dir"() {
        given:
        file('coolAppDir').createDir()

        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ear'
            apply plugin: 'eclipse-wtp'

            appDirName = 'coolAppDir'
        """

        when:
        succeeds('eclipse')

        then:
        componentFile.text.contains('coolAppDir')
    }

    @Issue("GRADLE-1974")
    def "may use web libraries container"() {
        given:
        //adding a little bit more stress with a subproject and some web resources:
        file("src/main/webapp/index.jsp") << "<html>Hey!</html>"
        file("settings.gradle") << "include 'someCoolLib'"

        buildFile << """
            apply plugin: 'war'
            apply plugin: 'eclipse-wtp'

            project(':someCoolLib') {
              apply plugin: 'java'
              apply plugin: 'eclipse-wtp'
            }

            repositories { mavenCentral() }

            dependencies {
              compile 'commons-io:commons-io:1.4'
              compile project(':someCoolLib')
            }
        """

        when:
        succeeds('eclipse')

        then: //the container is configured
        classpathFile.text.contains(EclipseWtpPlugin.WEB_LIBS_CONTAINER)
    }

    @Issue("GRADLE-1974")
    def "the web container is not present without war+wtp combo"() {
        given:
        buildFile << """
            apply plugin: 'java' //anything but not war
            apply plugin: 'eclipse-wtp'
        """

        when:
        succeeds('eclipse')

        then: //container is added only once:
        !classpathFile.text.contains(EclipseWtpPlugin.WEB_LIBS_CONTAINER)
    }

    @Issue("GRADLE-1707")
    def "the library and variable classpath entries are marked as component non-dependency"() {
        given:
        file('libs/myFoo.jar').touch()

        buildFile << """
            apply plugin: 'war'
            apply plugin: 'eclipse-wtp'

            repositories { mavenCentral() }

            dependencies {
              compile 'commons-io:commons-io:1.4'
              compile files('libs/myFoo.jar')
            }

            eclipse.pathVariables MY_LIBS: file('libs')
        """

        when:
        succeeds('eclipse')
        def classpath = getClasspath()
        def component = getWtpComponent()

        then:
        //the jar dependency is configured in the WTP component file and in the classpath
        classpath.lib('commons-io-1.4.jar').assertIsExcludedFromDeployment()
        component.lib('commons-io-1.4.jar').assertDeployedAt('/WEB-INF/lib')

        classpath.lib('myFoo.jar').assertIsExcludedFromDeployment()
        component.lib('myFoo.jar').assertDeployedAt('/WEB-INF/lib')
    }

    @Issue("GRADLE-1707")
    def "classpath entries are protected from conflicting component dependency attributes"() {
        given:
        buildFile << """
            apply plugin: 'war'
            apply plugin: 'eclipse-wtp'

            repositories { mavenCentral() }

            dependencies {
              compile 'commons-io:commons-io:1.4'
            }

            import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry

            eclipse.classpath.file.whenMerged { cp ->
              cp.entries.each {
                if(it instanceof AbstractClasspathEntry) {
                  //some people have workarounds in their builds and configure the component dependency,
                  //just like here:
                  it.entryAttributes[AbstractClasspathEntry.COMPONENT_DEPENDENCY_ATTRIBUTE] = 'WEB-INF/lib'
                }
              }
            }
        """

        when:
        succeeds('eclipse')

        then:
        classpath.lib('commons-io-1.4.jar').assertIsDeployedTo('WEB-INF/lib')
    }

    @Issue("GRADLE-1412")
    def "dependent project's library and variable classpath entries contain necessary dependency attribute"() {
        given:
        file('libs/myFoo.jar').touch()
        settingsFile << "include 'someLib'"

        buildFile << """
          apply plugin: 'war'
          apply plugin: 'eclipse-wtp'

          dependencies {
            compile project(':someLib')
          }

          project(':someLib') {
            apply plugin: 'java'
            apply plugin: 'eclipse-wtp'

            repositories { mavenCentral() }

            dependencies {
              compile 'commons-io:commons-io:1.4'
              compile files('libs/myFoo.jar')
            }

            eclipse.pathVariables MY_LIBS: file('libs')
          }
        """

        when:
        succeeds('eclipse')
        def classpath = classpath('someLib')

        then:
        classpath.lib('commons-io-1.4.jar').assertIsDeployedTo('../')
        classpath.lib('myFoo.jar').assertIsDeployedTo('../')
    }
}
