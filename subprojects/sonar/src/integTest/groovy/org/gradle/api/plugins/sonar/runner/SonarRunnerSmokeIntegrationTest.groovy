/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.plugins.sonar.runner

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.AvailablePortFinder
import org.gradle.util.ClasspathUtil
import org.junit.Rule
import org.mortbay.jetty.Server
import org.mortbay.jetty.webapp.WebAppContext
import spock.lang.AutoCleanup
import spock.lang.Shared

class SonarRunnerSmokeIntegrationTest extends AbstractIntegrationSpec {
    @Shared
    AvailablePortFinder portFinder = AvailablePortFinder.createPrivate()

    @AutoCleanup("stop")
    Server webServer = new Server(0)

    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider()

    @Rule
    TestResources testResources = new TestResources(temporaryFolder)

    int databasePort

    def setup() {
        def classpath = ClasspathUtil.getClasspath(getClass().classLoader).collect() { new File(it.toURI()) }
        def warFile = classpath.find { it.name == "sonar-test-server-3.4.war" }
        assert warFile
        def zipFile = classpath.find { it.name == "sonar-test-server-home-dir-3.4.0.1.zip" }
        assert zipFile

        def sonarHome = tempDir.createDir("sonar-home")
        System.setProperty("SONAR_HOME", sonarHome.path)
        new AntBuilder().unzip(src: zipFile, dest: sonarHome, overwrite: true)

        databasePort = portFinder.nextAvailable
        sonarHome.file("conf/sonar.properties") << """
sonar.jdbc.username=sonar
sonar.jdbc.password=sonar
sonar.jdbc.url=jdbc:h2:mem:sonartest
sonar.embeddedDatabase.port=$databasePort
        """.trim()

        def context = new WebAppContext()
        context.war = warFile
        webServer.addHandler(context)
        webServer.start()
    }

    def "execute 'sonarRunner' task"() {
        when:
        executer.requireIsolatedDaemons()
                .requireGradleHome()
                .withArgument("-i")
                .withArgument("-PserverUrl=foo") // dummy value for configuring sonarAnalyze task
                .withArgument("-PdatabaseUrl=bar") // dummy value for configuring sonarAnalyze task
                .withArgument("-Dsonar.host.url=http://localhost:${webServer.connectors[0].localPort}")
                .withArgument("-Dsonar.jdbc.url=jdbc:h2:tcp://localhost:$databasePort/mem:sonartest")
                .withTasks("sonarRunner").run()

        then:
        noExceptionThrown()
    }
}
