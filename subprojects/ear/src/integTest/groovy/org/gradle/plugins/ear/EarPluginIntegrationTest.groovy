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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.TextUtil
import org.hamcrest.Matchers
import spock.lang.Unroll

import static org.testng.Assert.assertEquals

class EarPluginIntegrationTest extends AbstractIntegrationSpec {

    void "setup"() {
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
    libDirName 'CUSTOM/lib'

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
        ear.assertFileContent("META-INF/application.xml", Matchers.containsString("cool ear"))
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
        assertEquals(modules[0].ejb.text(), 'moduleA.jar')
        assertEquals(modules[1].web.'web-uri'.text(), 'moduleB.war')
    }

    @Unroll
    void "uses content from application xml located #location"() {
        def xsi = ["xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_6.xsd\""]

        if (JavaVersion.current().java8Compatible) {
            xsi = xsi.reverse()
        }

        def applicationXml = """<?xml version="1.0"?>
<application xmlns="http://java.sun.com/xml/ns/javaee" ${xsi.join(" ")} version="6">
  <application-name>customear</application-name>
  <display-name>displayname</display-name>
  <library-directory>mylib</library-directory>
</application>
"""

        file('META-INF/application.xml').createFile().write(applicationXml)
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
        // Since the application.xml file is generated (using the supplied content), it uses platform line separators
        ear.assertFileContent("META-INF/application.xml", TextUtil.toPlatformLineSeparators(applicationXml))

        where:
        location                      | metaInfFolder   | appConfig
        "in root folder"              | "META-INF"      | ""
        "in specified metaInf folder" | "customMetaInf" | "metaInf { from 'customMetaInf' }"
    }

    @Unroll
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
        applicationName = 'descriptor modification will not have any affect when application.xml already exists in source'
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
        location    | descriptorConfig   | appDirectory
        "specified" | "appDirName 'app'" | "app"
        "default"   | ""                 | "src/main/application"
    }

    void "works with existing descriptor containing a doctype declaration"() {
        //make sure that the test is not executed in embedded mode because test classpath is polluted with xerces
        //which means that test result is not representative for some java versions (i.e. JDK8)
        requireGradleHome()
        // We serve the DTD locally because the the parser actually pulls on this URL,
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
        assertEquals(roles[0]."role-name".text(), 'superman')
        assertEquals(roles[0].description.text(), 'This is the SUPERMAN role')
        assertEquals(roles[1]."role-name".text(), 'supergirl')
        assertEquals(roles[1].description.text(), 'This is the SUPERGIRL role')
    }

}
