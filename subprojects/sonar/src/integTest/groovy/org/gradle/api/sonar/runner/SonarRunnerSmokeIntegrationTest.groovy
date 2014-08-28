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

package org.gradle.api.sonar.runner
import groovy.json.JsonSlurper
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.AvailablePortFinder
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import static org.gradle.integtests.fixtures.UrlValidator.available

@Requires(TestPrecondition.JDK7_OR_EARLIER)
class SonarRunnerSmokeIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider()

    @Rule
    TestResources testResources = new TestResources(temporaryFolder)

    @Rule
    SonarServerRule sonarServerRule = new SonarServerRule(tempDir)

    def "execute 'sonarRunner' task"() {
        when:
        runSonarRunnerTask()

        then:
        noExceptionThrown()
        sonarServerRule.assertProjectPresent('org.gradle.test.sonar:SonarTestBuild')
    }

    private ExecutionResult runSonarRunnerTask() {
        executer
                .withArgument("-Dsonar.host.url=http://localhost:${sonarServerRule.httpPort}")
                .withArgument("-Dsonar.jdbc.url=jdbc:h2:tcp://localhost:${sonarServerRule.databasePort}/sonar")
                .withArgument("-Dsonar.jdbc.username=sonar")
                .withArgument("-Dsonar.jdbc.password=sonar")
                // sonar.dynamicAnalysis is deprecated since SonarQube 4.3
                .withDeprecationChecksDisabled()
                .withTasks("sonarRunner").run()
    }
}


class SonarServerRule implements TestRule {

    private TestNameTestDirectoryProvider provider
    private AvailablePortFinder portFinder

    private int databasePort
    private int httpPort

    private Process sonarProcess

    SonarServerRule(TestNameTestDirectoryProvider provider) {
        this.provider = provider
        this.portFinder = AvailablePortFinder.createPrivate()
    }

    int getDatabasePort() {
        return databasePort
    }

    @Override
    Statement apply(Statement base, Description description) {
        return [ evaluate: {
            try {
                startServer()
                assertDbIsEmpty()
                base.evaluate()
            } finally {
                stopServer()
            }
        } ] as Statement
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
                    '-cp', 'lib/*:conf', 'org.sonar.application.StartServer'
        )

        sonarProcess = processBuilder.start()

        // ProcessBuilder#inheritIO() is java >= 7 only
        inheritIO(sonarProcess.getInputStream(), System.out);
        available(serverUrl, "sonar")
        assert apiRequest('webservices/list').statusLine.statusCode < 400
    }

    private static void inheritIO(final InputStream src, final PrintStream dest) {
        new Thread(new Runnable() {
            public void run() {
                Scanner sc = new Scanner(src);
                while (sc.hasNextLine()) {
                    dest.println(sc.nextLine());
                }
            }
        }).start();
    }

    private TestFile prepareSonarHomeFolder() {
        databasePort = portFinder.nextAvailable
        httpPort = portFinder.nextAvailable
        def classpath = ClasspathUtil.getClasspath(getClass().classLoader).collect() { new File(it.toURI()) }
        def zipFile = classpath.find { it.name ==~ "sonarqube.*\\.zip" }
        assert zipFile

        new AntBuilder().unzip(src: zipFile, dest: provider.testDirectory, overwrite: true)
        TestFile sonarHome = provider.testDirectory.file(zipFile.name - '.zip')

        sonarHome.file("conf/sonar.properties") << """\n
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

    def apiRequest(String path) {
        HttpClient httpClient = new DefaultHttpClient();
        def request = new HttpGet("$serverUrl/api/$path");
        httpClient.execute(request)
    }

    void assertDbIsEmpty() {
        assert getResources().empty
    }

    String getServerUrl(){
        "http://localhost:$httpPort"
    }

    def getResources() {
        new JsonSlurper().parse(
                apiRequest('resources?format=json').entity.content
        )
    }

    void assertProjectPresent(String name) {
        assert getResources()*.key.contains(name)
    }
}
