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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

class EclipseWtpModelIntegrationTest extends AbstractEclipseIntegrationTest {

    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    String component

    @Test
    @ToBeFixedForConfigurationCache
    void allowsConfiguringEclipseWtp() {
        //given
        file('someExtraSourceDir').mkdirs()
        file('src/foo/bar').mkdirs()

        mavenRepo.module("gradle", "foo").publish()
        mavenRepo.module("gradle", "bar").publish()
        mavenRepo.module("gradle", "baz").publish()

        //when
        runEclipseTask """
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

        // Classpath
        def classpath = getClasspath()
        classpath.assertHasLibs('foo-1.0.jar', 'bar-1.0.jar', 'baz-1.0.jar')
        classpath.lib('foo-1.0.jar').assertIsDeployedTo('/WEB-INF/lib')
        classpath.lib('bar-1.0.jar').assertIsDeployedTo('/WEB-INF/lib')
        classpath.lib('baz-1.0.jar').assertIsExcludedFromDeployment()

        // Facets
        wtpFacets.assertFacetVersion('gradleFacet', '1.333')

        // Component
        def component = getWtpComponent()
        component.resources[0].assertAttributes('deploy-path': '/WEB-INF/classes', 'source-path': 'someExtraSourceDir')
        component.resources[1].assertAttributes('deploy-path': './deploy/foo/bar', 'source-path': './src/foo/bar')
        assert component.deployName =='someBetterDeployName'
        assert component.moduleProperties.'wbPropertyOne' == 'New York!'
        assert component.moduleProperties.'context-root' == 'killerApp'
        //contains('userHomeVariable') //TODO don't know how to test it at the moment
    }

    @Issue("GRADLE-2653")
    @Test
    @ToBeFixedForConfigurationCache
    void "wtp component respects configuration modifications"() {
        //given
        mavenRepo.module("gradle", "foo").publish()
        mavenRepo.module("gradle", "bar").publish()
        mavenRepo.module("gradle", "baz").publish()
        mavenRepo.module("gradle", "baz", "2.0").publish()

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'war'
apply plugin: 'eclipse-wtp'

repositories {
  maven { url "${mavenRepo.uri}" }
}

dependencies {
  implementation 'gradle:foo:1.0', 'gradle:bar:1.0', 'gradle:baz:1.0'
}

configurations.all {
  exclude module: 'bar' //an exclusion
  resolutionStrategy.force 'gradle:baz:2.0' //forced module
}
        """

        def classpath = getClasspath()
        classpath.assertHasLibs('foo-1.0.jar', 'baz-2.0.jar')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void allowsConfiguringHooksForComponent() {
        //given
        def componentFile = file('.settings/org.eclipse.wst.common.component')
        componentFile << '''<?xml version="1.0" encoding="UTF-8"?>
<project-modules id="moduleCoreId" project-version="2.0">
	<wb-module deploy-name="coolDeployName">
		<property name="context-root" value="root"/>
		<wb-resource deploy-path="/" source-path="src/main/webapp"/>
	</wb-module>
</project-modules>
'''

        //when
        runEclipseTask """
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

        //when
        component = getFile([:], '.settings/org.eclipse.wst.common.component').text

        //then
        assert component.contains('betterDeployName')
        assert !component.contains('coolDeployName')
        assert component.contains('<be>cool</be>')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void allowsConfiguringHooksForFacet() {
        //given
        def componentFile = file('.settings/org.eclipse.wst.common.project.facet.core.xml')
        componentFile << '''<?xml version="1.0" encoding="UTF-8"?>
<faceted-project>
	<fixed facet="jst.java"/>
	<fixed facet="jst.web"/>
	<installed facet="jst.web" version="2.4"/>
	<installed facet="jst.java" version="5.0"/>
	<installed facet="facet.one" version="1.0"/>
</faceted-project>
'''

        //when
        runEclipseTask """
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

        def facet = getFile([:], '.settings/org.eclipse.wst.common.project.facet.core.xml').text

        assert facet.contains('facet.one')
        assert facet.contains('facet.two')
        assert facet.contains('facet.three')

        assert facet.contains('<be>cool</be>')
    }

    @Issue("GRADLE-2661")
    @Test
    @ToBeFixedForConfigurationCache
    void "file dependencies respect plus minus configurations"() {
        //when
        runEclipseTask """
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

        def classpath = getClasspath()
        classpath.assertHasLibs('foo.txt', 'bar.txt', 'baz.txt')
        classpath.lib('foo.txt').assertIsDeployedTo('/WEB-INF/lib')
        classpath.lib('bar.txt').assertIsDeployedTo('/WEB-INF/lib')
        classpath.lib('baz.txt').assertIsExcludedFromDeployment()
    }

    @Test
    @Issue("GRADLE-1881")
    @ToBeFixedForConfigurationCache
    void "uses eclipse project name for wtp module dependencies"() {
        //given
        createDirs("impl", "contrib")
        def settings = file('settings.gradle')
        settings << "include 'impl', 'contrib'"

        def build = file('build.gradle')
        build << """
project(':impl') {
  apply plugin: 'java'
  apply plugin: 'war'
  apply plugin: 'eclipse-wtp'

  dependencies { implementation project(':contrib') }

  eclipse.project.name = 'cool-impl'
}

project(':contrib') {
  apply plugin: 'java'
  apply plugin: 'eclipse-wtp'
  //should not have war nor ear applied

  eclipse.project.name = 'cool-contrib'
}
"""
        //when
        executer.withTasks('eclipse').run()

        //then
        def implComponent = wtpComponent('impl')
        assert implComponent.deployName == 'cool-impl'
        assert implComponent.project('cool-contrib')

        def contribComponent = wtpComponent('contrib')
        assert contribComponent.deployName == 'cool-contrib'
    }

    @Test
    @Issue("GRADLE-1881")
    @ToBeFixedForConfigurationCache
    void "does not explode if dependent project does not have eclipse plugin"() {
        //given
        createDirs("impl", "contrib")
        def settings = file('settings.gradle')
        settings << "include 'impl', 'contrib'"

        def build = file('build.gradle')
        build << """
project(':impl') {
  apply plugin: 'java'
  apply plugin: 'war'
  apply plugin: 'eclipse-wtp'

  dependencies { implementation project(':contrib') }

  eclipse.project.name = 'cool-impl'
}

project(':contrib') {
  apply plugin: 'java'
}
"""
        //when
        executer.withTasks('eclipse').run()

        //then no exception thrown
    }

    @Test
    @Issue("GRADLE-2030")
    @ToBeFixedForConfigurationCache
    void "component for war plugin does not contain non-existing source and resource dirs"() {
        //given
        file('xxxSource').createDir()
        file('xxxResource').createDir()

        //when
        runEclipseTask """
          apply plugin: 'java'
          apply plugin: 'war'
          apply plugin: 'eclipse-wtp'

          sourceSets.main.java.srcDirs 'yyySource', 'xxxSource'

          eclipse.wtp.component {
            resource sourcePath: 'xxxResource', deployPath: 'deploy-xxx'
            resource sourcePath: 'yyyResource', deployPath: 'deploy-yyy'
          }
"""
        //then
        def component = getComponentFile().text

        assert component.contains('xxxSource')
        assert !component.contains('yyySource')

        assert component.contains('xxxResource')
        assert !component.contains('yyyResource')
    }

    @Test
    @Issue("GRADLE-2030")
    @ToBeFixedForConfigurationCache
    void "component for ear plugin does not contain non-existing source and resource dirs"() {
        //given
        file('xxxSource').createDir()
        file('xxxResource').createDir()

        //when
        runEclipseTask """
          apply plugin: 'java'
          apply plugin: 'ear'
          apply plugin: 'eclipse-wtp'

          sourceSets.main.java.srcDirs 'yyySource', 'xxxSource'

          ear.appDirectory = file 'nonexistentAppDir'

          eclipse.wtp.component {
            resource sourcePath: 'xxxResource', deployPath: 'deploy-xxx'
            resource sourcePath: 'yyyResource', deployPath: 'deploy-yyy'
          }
"""
        //then
        def component = getComponentFile().text

        assert component.contains('xxxSource')
        assert !component.contains('yyySource')

        assert component.contains('xxxResource')
        assert !component.contains('yyyResource')

        assert !component.contains('nonexistentAppDir')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "component for ear plugin contains the app dir"() {
        //given
        file('coolAppDir').createDir()

        //when
        runEclipseTask """
          apply plugin: 'java'
          apply plugin: 'ear'
          apply plugin: 'eclipse-wtp'

          ear.appDirectory = file 'coolAppDir'
"""
        //then
        def component = getComponentFile().text

        assert component.contains('coolAppDir')
    }

    @Test
    @Issue("GRADLE-1974")
    @ToBeFixedForConfigurationCache
    void "may use web libraries container"() {
        //given
        //adding a little bit more stress with a subproject and some web resources:
        file("src/main/webapp/index.jsp") << "<html>Hey!</html>"
        createDirs("someCoolLib")
        file("settings.gradle") << "include 'someCoolLib'"

        file("build.gradle") << """
            apply plugin: 'war'
            apply plugin: 'eclipse-wtp'

            project(':someCoolLib') {
              apply plugin: 'java'
              apply plugin: 'eclipse-wtp'
            }

            ${mavenCentralRepository()}

            dependencies {
              implementation 'commons-io:commons-io:1.4'
              implementation project(':someCoolLib')
            }
        """

        //when
        executer.withTasks("eclipse").run()

        //then the container is configured
        assert getClasspathFile().text.contains("org.eclipse.jst.j2ee.internal.web.container")
    }

    @Test
    @Issue("GRADLE-1974")
    @ToBeFixedForConfigurationCache
    void "the web container is not present without war+wtp combo"() {
        //given
        file("build.gradle") << """
            apply plugin: 'java' //anything but not war
            apply plugin: 'eclipse-wtp'
        """

        //when
        executer.withTasks("eclipse").run()

        //then container is added only once:
        assert !getClasspathFile().text.contains("org.eclipse.jst.j2ee.internal.web.container")
    }

    @Test
    @Issue("GRADLE-1707")
    @ToBeFixedForConfigurationCache
    void "classpath entries are protected from conflicting component dependency attributes"() {
        //given
        file("build.gradle") << """
            apply plugin: 'war'
            apply plugin: 'eclipse-wtp'

            ${mavenCentralRepository()}

            dependencies {
              implementation 'commons-io:commons-io:1.4'
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

        //when
        executer.withTasks("eclipse").run()

        //then
        def classpath = classpath
        classpath.lib('commons-io-1.4.jar').assertIsDeployedTo('WEB-INF/lib')
    }

    @Test
    @Issue("GRADLE-1412")
    @ToBeFixedForConfigurationCache
    void "utility project's library and variable classpath entries contain necessary dependency attribute"() {
        //given
        file('libs/myFoo.jar').touch()
        createDirs("someLib")
        file('settings.gradle') << "include 'someLib'"

        file("build.gradle") <<
        """apply plugin: 'java'
           apply plugin: 'eclipse-wtp'

           ${mavenCentralRepository()}

           dependencies {
               runtimeOnly 'commons-io:commons-io:1.4'
               runtimeOnly files('libs/myFoo.jar')
           }

           eclipse.pathVariables MY_LIBS: file('libs')
        """

        //when
        executer.withTasks("eclipse").run()

        //then
        def classpath = getClasspath()

        classpath.lib('commons-io-1.4.jar').assertIsExcludedFromDeployment()
        classpath.lib('myFoo.jar').assertIsExcludedFromDeployment()
    }

    @Test
    @Issue("GRADLE-1412")
    @ToBeFixedForConfigurationCache
    void "web project's library and variable classpath entries contain necessary dependency attribute"() {
        //given
        file('libs/myFoo.jar').touch()
        createDirs("someLib")
        file('settings.gradle') << "include 'someLib'"

        file("build.gradle") <<
        """apply plugin: 'war'
           apply plugin: 'eclipse-wtp'

           ${mavenCentralRepository()}

           dependencies {
               runtimeOnly 'commons-io:commons-io:1.4'
               runtimeOnly files('libs/myFoo.jar')
           }

           eclipse.pathVariables MY_LIBS: file('libs')
        """

        //when
        executer.withTasks("eclipse").run()

        //then
        def classpath = getClasspath()

        classpath.lib('commons-io-1.4.jar').assertIsDeployedTo('/WEB-INF/lib')
        classpath.lib('myFoo.jar').assertIsDeployedTo('/WEB-INF/lib')
    }

    protected def contains(String ... contents) {
        contents.each { assert component.contains(it)}
    }
}
