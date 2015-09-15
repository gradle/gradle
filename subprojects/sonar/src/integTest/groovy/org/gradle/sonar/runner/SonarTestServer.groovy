/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.sonar.runner

import groovy.json.JsonSlurper
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.gradle.integtests.fixtures.UrlValidator
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.ports.ReleasingPortAllocator
import org.junit.Rule
import org.junit.rules.ExternalResource

class SonarTestServer extends ExternalResource {

    private TestNameTestDirectoryProvider provider
    @Rule private ReleasingPortAllocator portFinder = new ReleasingPortAllocator()

    private int databasePort
    private int httpPort

    private Process sonarProcess

    SonarTestServer(TestNameTestDirectoryProvider provider, GradleExecuter gradleExecuter) {
        this.provider = provider

        gradleExecuter.beforeExecute {
            withArgument("-Dsonar.host.url=http://localhost:${httpPort}")
            withArgument("-Dsonar.jdbc.url=jdbc:h2:tcp://localhost:${databasePort}/sonar")
            withArgument("-Dsonar.jdbc.username=sonar")
            withArgument("-Dsonar.jdbc.password=sonar")
        }
    }

    @Override
    protected void before() throws Throwable {
        startServer()
        assertDbIsEmpty()
    }

    @Override
    protected void after() {
        stopServer()
    }

    void startServer() {
        TestFile sonarHome = prepareSonarHomeFolder()
        ProcessBuilder processBuilder = new ProcessBuilder()
                .directory(sonarHome)
                .redirectErrorStream(true)
                .command(
                Jvm.current().getJavaExecutable().absolutePath,
                '-XX:MaxPermSize=160m', '-Xmx512m', '-Djava.awt.headless=true',
                '-Dfile.encoding=UTF-8', '-Djruby.management.enabled=false',
                '-cp', "lib/*${File.pathSeparator}conf", 'org.sonar.application.StartServer'
        )

        sonarProcess = processBuilder.start()

        sonarProcess.consumeProcessOutput((Appendable) System.out, (Appendable) System.err)
        UrlValidator.available(serverUrl, "sonar")
        assert apiRequest('webservices/list').statusLine.statusCode < 400
    }

    private TestFile prepareSonarHomeFolder() {
        databasePort = portFinder.assignPort()
        httpPort = portFinder.assignPort()
        def classpath = ClasspathUtil.getClasspath(getClass().classLoader).collect() {
            new File(it.toURI())
        }
        def zipFile = classpath.find {
            it.name ==~ "sonarqube.*\\.zip"
        }
        assert zipFile

        new TestFile(zipFile).unzipTo(provider.testDirectory)
        TestFile sonarHome = provider.testDirectory.file(zipFile.name - '.zip')

        sonarHome.file("conf/sonar.properties") << """
            sonar.web.port=$httpPort
            sonar.jdbc.username=sonar
            sonar.jdbc.password=sonar
            sonar.jdbc.url=jdbc:h2:tcp://localhost:$databasePort/sonar
            sonar.embeddedDatabase.port=$databasePort
        """.stripIndent()

        sonarHome
    }

    void stopServer() {
        sonarProcess?.waitForOrKill(100)
    }

    HttpResponse apiRequest(String path) {
        def httpClient = new DefaultHttpClient()
        def request = new HttpGet(apiPath(path))
        httpClient.execute(request)
    }

    private String apiPath(String path) {
        "$serverUrl/api/$path"
    }

    void assertDbIsEmpty() {
        assert getResources().empty
    }

    String getServerUrl() {
        "http://localhost:$httpPort"
    }

    List<?> getResources() {
        new JsonSlurper().parse(apiRequest('resources?format=json').entity.content)
    }

    void assertProjectPresent(String name) {
        assert getResources()*.key.contains(name)
    }
}
