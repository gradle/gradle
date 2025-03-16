/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ear

import groovy.xml.XmlSlurper
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.gradle.test.fixtures.archive.JarTestFixture
import org.hamcrest.CoreMatchers
import spock.lang.Issue

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

@TestReproducibleArchives
class EarPluginIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        file("rootLib.jar").createNewFile()
        file("earLib.jar").createNewFile()

        file("settings.gradle").write("rootProject.name='root'")
    }

    void "creates ear archive"() {
        buildFile << """
apply plugin: 'ear'

dependencies {
    deploy files('rootLib.jar')
    earlib files('earLib.jar')
}

"""
        when:
        run 'assemble'

        then:
        def ear = new JarTestFixture(file('build/libs/root.ear'))
        ear.assertContainsFile("META-INF/MANIFEST.MF")
        ear.assertContainsFile("META-INF/application.xml")
        ear.assertContainsFile("rootLib.jar")
        ear.assertContainsFile("lib/earLib.jar")
    }

    void "customizes ear archive"() {
        buildFile << """
apply plugin: 'ear'

dependencies {
    earlib files('earLib.jar')
}

ear {
    libDirName = 'CUSTOM/lib'

    deploymentDescriptor {
        applicationName = "cool ear"
    }
}

"""
        when:
        run 'assemble'

        then:
        def ear = new JarTestFixture(file('build/libs/root.ear'))
        ear.assertContainsFile("CUSTOM/lib/earLib.jar")
        ear.assertFileContent("META-INF/application.xml", CoreMatchers.containsString("cool ear"))
        def appXml = new XmlSlurper().parseText(ear.content('META-INF/application.xml'))
        appXml.'library-directory'.text() == 'CUSTOM/lib'
    }

    void "includes modules in deployment descriptor"() {
        file('moduleA.jar').createFile()
        file('moduleB.war').createFile()

        buildFile << """
apply plugin: 'ear'

dependencies {
    deploy files('moduleA.jar', 'moduleB.war')
}
"""
        when:
        run 'assemble'
        file("build/libs/root.ear").unzipTo(file("unzipped"))

        then:
        def appXml = new XmlSlurper().parse(
            file('unzipped/META-INF/application.xml'))
        def modules = appXml.module
        modules[0].ejb.text() == 'moduleA.jar'
        modules[1].web.'web-uri'.text() == 'moduleB.war'
    }

    void "uses content from application xml located #location"() {
        def xsi = ["xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_6.xsd\""]

        if (JavaVersion.current().java8Compatible) {
            xsi = xsi.reverse()
        }

        // Use platform line separators here, so that we get the same result for customMetaInf and default.
        // The default application.xml file is generated (using the supplied content), and always contains platform line separators
        def applicationXml = toPlatformLineSeparators("""<?xml version="1.0"?>
<application xmlns="http://java.sun.com/xml/ns/javaee" ${xsi.join(" ")} version="6">
  <application-name>customear</application-name>
  <display-name>displayname</display-name>
  <library-directory>mylib-$metaInfFolder</library-directory>
</application>
""")

        file("$metaInfFolder/application.xml").createFile().write(applicationXml)
        buildFile << """
apply plugin: 'ear'
ear {
    $appConfig
}
"""

        when:
        run 'assemble'

        then:
        def ear = new JarTestFixture(file('build/libs/root.ear'))
        ear.assertFileContent("META-INF/application.xml", applicationXml)

        where:
        location                      | metaInfFolder   | appConfig
        "in root folder"              | "META-INF"      | ""
        "in specified metaInf folder" | "customMetaInf" | "metaInf { from 'customMetaInf' }"
    }

    void "skips creating application xml"() {
        buildFile << """
apply plugin: 'ear'
ear {
    generateDeploymentDescriptor = false
}
"""

        when:
        run 'assemble'

        then:
        def ear = new JarTestFixture(file('build/libs/root.ear'))
        ear.assertNotContainsFile("META-INF/application.xml")
    }

    void "uses content found in #location app folder, ignoring descriptor modification"() {
        def applicationXml = """<?xml version="1.0"?>
<application xmlns="http://java.sun.com/xml/ns/javaee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_6.xsd" version="6">
  <application-name>customear</application-name>
</application>
"""

        file("${appDirectory}/META-INF/application.xml").createFile().write(applicationXml)
        file("${appDirectory}/someOtherFile.txt").createFile()
        file("${appDirectory}/META-INF/stuff/yetAnotherFile.txt").createFile()
        buildFile << """
apply plugin: 'ear'

ear {
    ${descriptorConfig}
    deploymentDescriptor {
        applicationName = 'descriptor modification will not have any effect when application.xml already exists in source'
    }
}
"""

        when:
        run 'assemble'

        then:
        def ear = new JarTestFixture(file('build/libs/root.ear'))
        ear.assertContainsFile("someOtherFile.txt")
        ear.assertContainsFile("META-INF/stuff/yetAnotherFile.txt")
        ear.assertFileContent("META-INF/application.xml", applicationXml)

        where:
        location    | descriptorConfig                                                                   | appDirectory
        "specified" | "tasks.named('ear') { appDirectory = project.layout.projectDirectory.dir('app') }" | "app"
        "default"   | ""                                                                                 | "src/main/application"
    }

    void "works with existing descriptor containing a doctype declaration"() {
        // We serve the DTD locally because the parser actually pulls on this URL,
        // and we don't want it reaching out to the Internet in our tests
        def dtdResource = getClass().getResource("application_1_3.dtd")
        assert dtdResource != null

        def applicationXml = """<?xml version="1.0"?>
<!DOCTYPE application PUBLIC "-//Sun Microsystems, Inc.//DTD J2EE Application 1.3//EN" "$dtdResource">
<application xmlns="http://java.sun.com/xml/ns/javaee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_6.xsd" version="6">
  <application-name>customear</application-name>
</application>
"""

        file('src/main/application/META-INF/application.xml').createFile().write(applicationXml)
        buildFile << """
apply plugin: 'ear'
"""

        when:
        run 'assemble'

        then:
        def ear = new JarTestFixture(file('build/libs/root.ear'))
        ear.assertFileContent("META-INF/application.xml", applicationXml)
    }

    void "exclude duplicates: lib has priority over other files"() {
        file('bad-lib/file.txt').createFile().write('bad')
        file('good-lib/file.txt').createFile().write('good')

        buildFile << '''
apply plugin: 'ear'
ear {
   duplicatesStrategy = 'exclude'
   into('lib') {
       from 'bad-lib'
   }
   lib {
       from 'good-lib'
   }
}'''

        when:
        run 'assemble';

        then:
        def ear = new JarTestFixture(file('build/libs/root.ear'))
        ear.assertFileContent("lib/file.txt", "good")
    }

    void "use security role closure"() {
        file('bad-lib/file.txt').createFile().write('bad')
        file('good-lib/file.txt').createFile().write('good')

        buildFile << '''
apply plugin: 'ear'
ear {
  deploymentDescriptor {

    securityRole {
      roleName="superman"
      description="This is the SUPERMAN role"
     }

    securityRole {
      roleName="supergirl"
      description="This is the SUPERGIRL role"
    }
  }
}'''

        when:
        run 'assemble';

        file("build/libs/root.ear").unzipTo(file("unzipped"))

        then:
        def appXml = new XmlSlurper().parse(
            file('unzipped/META-INF/application.xml'))
        def roles = appXml."security-role"
        roles[0]."role-name".text() == 'superman'
        roles[0].description.text() == 'This is the SUPERMAN role'
        roles[1]."role-name".text() == 'supergirl'
        roles[1].description.text() == 'This is the SUPERGIRL role'
    }

    @Issue("GRADLE-3471")
    def "does not fail when an ear has a war to deploy and a module defined with the same path"() {
        buildFile << """
apply plugin: 'ear'
apply plugin: 'war'

dependencies {
    deploy files(tasks.war)
}

ear {
    deploymentDescriptor {
        applicationName = "OurAppName"
        webModule("root.war", "anywhere")
    }
}

"""
        when:
        run 'assemble'
        and:
        file("build/libs/root.ear").unzipTo(file("unzipped"))

        then:
        def ear = new JarTestFixture(file('build/libs/root.ear'))
        ear.assertContainsFile("META-INF/MANIFEST.MF")
        ear.assertContainsFile("META-INF/application.xml")
        def appXml = new XmlSlurper().parse(file('unzipped/META-INF/application.xml'))
        def module = appXml.module[0].web
        module."web-uri" == "root.war"
        module."context-root" == "anywhere"
    }

    @Issue("GRADLE-3486")
    def "does not fail when provided with an existing descriptor without a version attribute"() {
        given:
        buildFile '''
            apply plugin: 'ear'
        '''.stripIndent()
        createDir('src/main/application/META-INF') {
            file('application.xml').text = '''
                <?xml version="1.0"?>
                <application xmlns="http://java.sun.com/xml/ns/javaee" xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_6.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                </application>
            '''.stripIndent().trim()
        }

        when:
        run 'assemble'

        then:
        def ear = new JarTestFixture(file('build/libs/root.ear'))
        ear.assertContainsFile("META-INF/application.xml")
    }

    def "does not fail when initializeInOrder is null"() {
        given:
        buildFile '''
            apply plugin: 'ear'
            ear {
                deploymentDescriptor {
                    initializeInOrder = null
                }
            }
        '''.stripIndent()

        when:
        run 'assemble'

        then:
        def ear = new JarTestFixture(file('build/libs/root.ear'))
        ear.assertContainsFile("META-INF/application.xml")
    }

    @Issue("GRADLE-3497")
    def "does not fail when provided with an existing descriptor with security roles without description"() {
        given:
        buildFile '''
            apply plugin: 'ear'
        '''.stripIndent()
        createDir('src/main/application/META-INF') {
            file('application.xml').text = '''
                <application>
                  <security-role>
                    <role-name>ROLE_ADMINISTRATOR</role-name>
                  </security-role>
                  <security-role>
                    <role-name>ROLE_USER</role-name>
                  </security-role>
                </application>
            '''.stripIndent().trim()
        }

        when:
        run 'assemble'

        then:
        def ear = new JarTestFixture(file('build/libs/root.ear'))
        ear.assertContainsFile("META-INF/application.xml")
    }

    @Issue("GRADLE-3497")
    def "does not fail when provided with an existing descriptor with a web module without #missing"() {
        given:
        buildFile '''
            apply plugin: 'ear'
        '''.stripIndent()
        createDir('src/main/application/META-INF') {
            file('application.xml').text = """
                <application>
                  <module>
                    <web>
                      $webModuleContent
                    </web>
                  </module>
                </application>
            """.stripIndent().trim()
        }

        when:
        run 'assemble'

        then:
        def ear = new JarTestFixture(file('build/libs/root.ear'))
        ear.assertContainsFile("META-INF/application.xml")

        where:
        missing        | webModuleContent
        'web-uri'      | '<context-root>Test</context-root>'
        'context-root' | '<web-uri>My.war</web-uri>'
    }

    @Issue("gradle/gradle#1092")
    def "can use Ear task without ear plugin"() {
        file("src/file").text = "foo"

        buildFile << """
            task ear(type: Ear) {
                from("src")
                lib {
                    from("rootLib.jar")
                }
                archiveFileName = "test.ear"
                destinationDirectory = temporaryDir
            }
        """
        when:
        succeeds("ear")
        then:
        def ear = new JarTestFixture(file('build/tmp/ear/test.ear'))
        // default location should be 'lib'
        ear.assertContainsFile("lib/rootLib.jar")
    }

    @Issue("https://github.com/gradle/gradle/issues/19725")
    def "can apply ear plugin to empty project"() {
        given:
        settingsFile << """
        rootProject.name = 'empty-project'
        """
        buildFile << """
            apply plugin: 'ear'
        """

        when:
        succeeds 'assemble'
        succeeds 'assemble'

        then:
        def ear = new JarTestFixture(file('build/libs/empty-project.ear'))
        ear.assertContainsFile("META-INF/MANIFEST.MF")
        ear.assertContainsFile("META-INF/application.xml")
    }

    def "ear contains runtime classpath of upstream java project"() {
        given:
        createDirs("a", "b", "c", "d", "e")
        file("settings.gradle") << """
            include "a", "b", "c", "d", "e"
        """

        and:
        buildFile << """
            project(":a") {
                apply plugin: 'ear'
                dependencies {
                    earlib project(":b")
                }
            }
            project(":b") {
                apply plugin: 'java-library'
                dependencies {
                    api project(':c')
                }
            }
            project(":c") {
                apply plugin: 'java'
                dependencies {
                    implementation project(':d')
                    compileOnly project(':e')
                }
            }
            project(":d") {
                apply plugin: 'java'
            }
            project(":e") {
                apply plugin: 'java'
            }
        """

        when:
        run 'assemble'

        then:
        def ear = new JarTestFixture(file('a/build/libs/a.ear'))
        ear.assertContainsFile("lib/b.jar")
        ear.assertContainsFile("lib/c.jar")
        ear.assertContainsFile("lib/d.jar")
        ear.assertNotContainsFile("lib/e.jar")
    }

    def "ear contains runtime classpath of upstream java-library project"() {
        given:
        createDirs("a", "b", "c", "d", "e")
        file("settings.gradle") << """
            include "a", "b", "c", "d", "e"
        """

        and:
        buildFile << """
            project(":a") {
                apply plugin: 'ear'
                dependencies {
                    earlib project(":b")
                }
            }
            project(":b") {
                apply plugin: 'java-library'
                dependencies {
                    api project(':c')
                    compileOnly project(':e')
                }
            }
            project(":c") {
                apply plugin: 'java-library'
                dependencies {
                    implementation project(':d')
                }
            }
            project(":d") {
                apply plugin: 'java-library'
            }
            project(":e") {
                apply plugin: 'java-library'
            }
        """

        when:
        run 'assemble'

        then:
        def ear = new JarTestFixture(file('a/build/libs/a.ear'))
        ear.assertContainsFile("lib/b.jar")
        ear.assertContainsFile("lib/c.jar")
        ear.assertContainsFile("lib/d.jar")
        ear.assertNotContainsFile("lib/e.jar")
    }

    def "using nested descriptor file name is not allowed"() {
        buildFile '''
            apply plugin: 'ear'

            ear {
                deploymentDescriptor {
                    fileName = 'nested/blubb.xml'
                    applicationName = 'NestedDemo'

                }
            }

        '''.stripIndent()

        when:
        fails 'assemble'

        then:
        failure.assertHasCause("Deployment descriptor file name must be a simple name but was nested/blubb.xml")
    }

}
