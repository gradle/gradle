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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.junit.Before
import org.junit.Test
import org.testng.Assert

import static org.testng.Assert.assertEquals
import static org.testng.Assert.assertFalse
import static org.testng.Assert.assertTrue

/**
 * @author: Szczepan Faber, created at: 6/3/11
 */
class EarPluginIntegrationTest extends AbstractIntegrationTest {

    @Before
    void "boring setup"() {
        file("rootLib.jar").createNewFile()
        file("earLib.jar").createNewFile()

        file("settings.gradle").write("rootProject.name='root'")
    }

    @Test
    void "creates ear archive"() {
        file("build.gradle").write("""
apply plugin: 'ear'

dependencies {
    deploy files('rootLib.jar')
    earlib files('earLib.jar')
}

""")
        //when
        executer.withTasks('assemble').run()
        file("build/libs/root.ear").unzipTo(file("unzipped"))

        //then
        file("unzipped/rootLib.jar").assertExists()
        file("unzipped/META-INF/MANIFEST.MF").assertExists()
        file("unzipped/META-INF/application.xml").assertExists()
        file("unzipped/lib/earLib.jar").assertExists()
    }

    @Test
    void "customizes ear archive"() {
        file("build.gradle").write("""
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

""")
        //when
        executer.withTasks('assemble').run()
        file("build/libs/root.ear").unzipTo(file("unzipped"))

        //then
        file("unzipped/CUSTOM/lib/earLib.jar").assertExists()
        assert file("unzipped/META-INF/application.xml").text.contains('cool ear')
    }


    @Test
    void "includes modules in deployment descriptor"() {
        file('moduleA.jar').createFile()
        file('moduleB.war').createFile()

        file("build.gradle").write("""
apply plugin: 'ear'

dependencies {
    deploy files('moduleA.jar', 'moduleB.war')
}
""")
        //when
        executer.withTasks('assemble').run()
        file("build/libs/root.ear").unzipTo(file("unzipped"))

        //then
        def appXml = new XmlSlurper().parse(
                file('unzipped/META-INF/application.xml'))
        def modules = appXml.module
        assertEquals(modules[0].ejb.text(), 'moduleA.jar')
        assertEquals(modules[1].web.'web-uri'.text(), 'moduleB.war')
    }

    @Test
    void "uses content found in specified app folder"() {
        def applicationXml = """<?xml version="1.0"?>
<application xmlns="http://java.sun.com/xml/ns/javaee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_6.xsd" version="6">
  <application-name>customear</application-name>
</application>
"""

        file('app/META-INF/application.xml').createFile().write(applicationXml)
        file('app/someOtherFile.txt').createFile()
        file('app/META-INF/stuff/yetAnotherFile.txt').createFile()
        file("build.gradle").write("""
apply plugin: 'ear'

ear {
  appDirName 'app'
}
""")

        //when
        executer.withTasks('assemble').run()
        file("build/libs/root.ear").unzipTo(file("unzipped"))

        //then
        assert file("unzipped/someOtherFile.txt").assertExists()
        assert file("unzipped/META-INF/stuff/yetAnotherFile.txt").assertExists()
        assert file("unzipped/META-INF/application.xml").text == applicationXml
    }

    @Test
    void "uses content found in default app folder"() {
        def applicationXml = """<?xml version="1.0"?>
<application xmlns="http://java.sun.com/xml/ns/javaee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_6.xsd" version="6">
  <application-name>customear</application-name>
</application>
"""

        file('src/main/application/META-INF/application.xml').createFile().write(applicationXml)
        file('src/main/application/someOtherFile.txt').createFile()
        file('src/main/application/META-INF/stuff/yetAnotherFile.txt').createFile()
        file("build.gradle").write("""
apply plugin: 'ear'
ear {
    deploymentDescriptor {
        applicationName = 'descriptor modification will not have any affect when application.xml already exists in source'
    }
}
""")

        //when
        executer.withTasks('assemble').run()
        file("build/libs/root.ear").unzipTo(file("unzipped"))

        //then
        assert file("unzipped/someOtherFile.txt").assertExists()
        assert file("unzipped/META-INF/stuff/yetAnotherFile.txt").assertExists()
        assert file("unzipped/META-INF/application.xml").text == applicationXml
    }

    @Test
    void "exclude duplicates: deploymentDescriptor has priority over metaInf"() {
        file('bad-meta-inf/application.xml').createFile().write('bad descriptor')
        file('build.gradle').write('''
apply plugin: 'ear'
ear {
   duplicatesStrategy = 'exclude'
   metaInf {
       from 'bad-meta-inf'
   }
   deploymentDescriptor {
       applicationName = 'good'
   }
}''')

        // when
        executer.withTasks('assemble').run();
        file('build/libs/root.ear').unzipTo(file('unzipped'))

        // then
        assertFalse(
                file('unzipped/META-INF/application.xml').text.contains('bad descriptor'))

    }

    @Test
    void "exclude duplicates: lib has priority over other files"() {
        file('bad-lib/file.txt').createFile().write('bad')
        file('good-lib/file.txt').createFile().write('good')

        file('build.gradle').write('''
apply plugin: 'ear'
ear {
   duplicatesStrategy = 'exclude'
   into('lib') {
       from 'bad-lib'
   }
   lib {
       from 'good-lib'
   }
}''')

        // when
        executer.withTasks('assemble').run();
        file('build/libs/root.ear').unzipTo(file('unzipped'))

        // then
        assertEquals('good', file('unzipped/lib/file.txt').text)

    }

}
