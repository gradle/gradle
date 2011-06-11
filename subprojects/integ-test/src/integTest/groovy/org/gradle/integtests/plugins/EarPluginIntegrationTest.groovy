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

package org.gradle.integtests.plugins

import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.junit.Before
import org.junit.Test

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
    //TODO SF for some awkard reason, setting this property won't work...
    libDirName 'CUSTOM/lib'

    deploymentDescriptor {
        applicationName = "cool ear"
//        TODO SF: cover some other fields as well
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
    void "enables jar"() {
        file("build.gradle").write("""
apply plugin: 'ear'
apply plugin: 'java'

dependencies {
    earlib files('earLib.jar')
}

jar.enabled = true
""")
        //when
        executer.withTasks('assemble').run()
        //then
        file("build/libs/root.ear").assertExists()
        file("build/libs/root.jar").assertExists()
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
}
