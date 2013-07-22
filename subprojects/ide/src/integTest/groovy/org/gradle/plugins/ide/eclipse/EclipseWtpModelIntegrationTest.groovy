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

import org.gradle.integtests.fixtures.TestResources
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

class EclipseWtpModelIntegrationTest extends AbstractEclipseIntegrationTest {

    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    String component

    @Test
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

  pathVariables 'userHomeVariable' : file(System.properties['user.home'])

  wtp {
    component {
      contextPath = 'killerApp'

      sourceDirs += file('someExtraSourceDir')

      plusConfigurations += configurations.configOne
      minusConfigurations += configurations.configTwo

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

        component = getFile([:], '.settings/org.eclipse.wst.common.component').text
        def facet = getFile([:], '.settings/org.eclipse.wst.common.project.facet.core.xml').text

        //then component:
        contains('someExtraSourceDir')

        contains('foo-1.0.jar', 'bar-1.0.jar')
        assert !component.contains('baz-1.0.jar')

        contains('someBetterDeployName')

        //contains('userHomeVariable') //TODO don't know how to test it at the moment

        contains('./src/foo/bar', './deploy/foo/bar')
        contains('wbPropertyOne', 'New York!')

        contains('killerApp')

        assert facet.contains('gradleFacet')
        assert facet.contains('1.333')
    }

    @Issue("GRADLE-2653")
    @Test
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
  compile 'gradle:foo:1.0', 'gradle:bar:1.0', 'gradle:baz:1.0'
}

configurations.compile {
  exclude module: 'bar' //an exclusion
  resolutionStrategy.force 'gradle:baz:2.0' //forced module
}
        """

        //when
        component = getFile([:], '.settings/org.eclipse.wst.common.component').text

        //then
        component.contains('foo-1.0.jar')
        component.contains('baz-2.0.jar') //forced version
        !component.contains('bar') //excluded
    }

    @Test
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
        plusConfigurations += configurations.configOne
        minusConfigurations += configurations.configTwo
    }
  }
}
        """

        def component = getFile([:], '.settings/org.eclipse.wst.common.component').text
        assert component.contains('foo.txt')
        assert component.contains('bar.txt')
        assert !component.contains('baz.txt')
    }

    @Test
    void createsTasksOnDependantUponProjectsEvenIfTheyDontHaveWarPlugin() {
        //given
        def settings = file('settings.gradle')
        settings << "include 'impl', 'contrib'"

        def build = file('build.gradle')
        build << """
project(':impl') {
  apply plugin: 'java'
  apply plugin: 'war'
  apply plugin: 'eclipse-wtp'

  dependencies { compile project(':contrib') }
}

project(':contrib') {
  apply plugin: 'java'
  apply plugin: 'eclipse-wtp'
}
"""
        //when
        executer.usingSettingsFile(settings).usingBuildScript(build).withTasks('eclipse').run()

        //then
        assert getComponentFile(project: 'impl').exists()
        assert getFacetFile(project: 'impl').exists()

        assert getComponentFile(project: 'contrib').exists()
        assert getFacetFile(project: 'contrib').exists()
    }

    @Test
    @Issue("GRADLE-1881")
    void "uses eclipse project name for wtp module dependencies"() {
        //given
        def settings = file('settings.gradle')
        settings << "include 'impl', 'contrib'"

        def build = file('build.gradle')
        build << """
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
        //when
        executer.usingSettingsFile(settings).usingBuildScript(build).withTasks('eclipse').run()

        //then
        //the deploy name is correct:
        assert getComponentFile(project: 'impl').text.contains('deploy-name="cool-impl"')
        //the dependent-module name is correct:
        assert getComponentFile(project: 'impl').text.contains('handle="module:/resource/cool-contrib/cool-contrib"')
        //the submodule name is correct:
        assert getComponentFile(project: 'contrib').text.contains('deploy-name="cool-contrib"')
    }

    @Test
    @Issue("GRADLE-1881")
    void "does not explode if dependent project does not have eclipse plugin"() {
        //given
        def settings = file('settings.gradle')
        settings << "include 'impl', 'contrib'"

        def build = file('build.gradle')
        build << """
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
        //when
        executer.usingSettingsFile(settings).usingBuildScript(build).withTasks('eclipse').run()

        //then no exception thrown
    }

    @Test
    @Issue("GRADLE-2030")
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

          appDirName = 'nonExistingAppDir'

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
        
        assert !component.contains('nonExistingAppDir')
    }

    @Test
    void "component for ear plugin contains the app dir"() {
        //given
        file('coolAppDir').createDir()

        //when
        runEclipseTask """
          apply plugin: 'java'
          apply plugin: 'ear'
          apply plugin: 'eclipse-wtp'

          appDirName = 'coolAppDir'
"""
        //then
        def component = getComponentFile().text

        assert component.contains('coolAppDir')
    }

    @Test
    @Issue("GRADLE-1974")
    void "may use web libraries container"() {
        //given
        //adding a little bit more stress with a subproject and some web resources:
        file("src/main/webapp/index.jsp") << "<html>Hey!</html>"
        file("settings.gradle") << "include 'someCoolLib'"

        file("build.gradle") << """
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

        //when
        executer.withTasks("eclipse").run()

        //then the container is configured
        assert getClasspathFile().text.contains(EclipseWtpPlugin.WEB_LIBS_CONTAINER)
    }

    @Test
    @Issue("GRADLE-1974")
    void "the web container is not present without war+wtp combo"() {
        //given
        file("build.gradle") << """
            apply plugin: 'java' //anything but not war
            apply plugin: 'eclipse-wtp'
        """

        //when
        executer.withTasks("eclipse").run()

        //then container is added only once:
        assert !getClasspathFile().text.contains(EclipseWtpPlugin.WEB_LIBS_CONTAINER)
    }

    @Test
    @Issue("GRADLE-1707")
    void "the library and variable classpath entries are marked as component non-dependency"() {
        //given
        file('libs/myFoo.jar').touch()

        file("build.gradle") << """
            apply plugin: 'war'
            apply plugin: 'eclipse-wtp'

            repositories { mavenCentral() }

            dependencies {
              compile 'commons-io:commons-io:1.4'
              compile files('libs/myFoo.jar')
            }

            eclipse.pathVariables MY_LIBS: file('libs')
        """

        //when
        executer.withTasks("eclipse").run()

        //then
        def classpath = getClasspathFile(print: true).text
        def component = getComponentFile().text

        //the jar dependency is configured in the WTP component file and in the classpath
        assert classpath.contains('commons-io')
        assert component.contains('commons-io')

        assert classpath.contains('kind="var" path="MY_LIBS/myFoo.jar"')
        assert component.contains('myFoo.jar')

        //the jar dependencies are configured as non-dependency in the .classpath
        classpath.count(AbstractClasspathEntry.COMPONENT_NON_DEPENDENCY_ATTRIBUTE) == 2
        classpath.count(AbstractClasspathEntry.COMPONENT_DEPENDENCY_ATTRIBUTE) == 0
    }

    @Test
    @Issue("GRADLE-1707")
    void "classpath entries are protected from conflicting component dependency attributes"() {
        //given
        file("build.gradle") << """
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

        //when
        executer.withTasks("eclipse").run()

        //then
        def classpath = getClasspathFile(print: true).text
        //component dependency wins:
        assert classpath.contains(AbstractClasspathEntry.COMPONENT_DEPENDENCY_ATTRIBUTE)
        //non-dependency (our default) loses:
        assert !classpath.contains(AbstractClasspathEntry.COMPONENT_NON_DEPENDENCY_ATTRIBUTE)
    }

    @Test
    @Issue("GRADLE-1412")
    void "dependent project's library and variable classpath entries contain necessary dependency attribute"() {
        //given
        file('libs/myFoo.jar').touch()
        file('settings.gradle') << "include 'someLib'"

        file("build.gradle") << """
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

        //when
        executer.withTasks("eclipse").run()

        //then
        def classpath = getClasspathFile(project: 'someLib', print: true).text

        //contains both entries
        assert classpath.contains('kind="var" path="MY_LIBS/myFoo.jar"')
        assert classpath.contains('commons-io')

        //both var and lib entries have the attribute
        classpath.count('<attribute name="org.eclipse.jst.component.dependency" value="../"/>') == 2
    }

    protected def contains(String ... contents) {
        contents.each { assert component.contains(it)}
    }
}
