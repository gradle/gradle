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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.nativeintegration.jansi.JansiLibraryFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.internal.nativeintegration.jansi.JansiBootPathConfigurer.JANSI_LIBRARY_PATH_SYS_PROP

class NativeServicesIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def libraryFactory = new JansiLibraryFactory()
    def nativeDir = new File(executer.gradleUserHomeDir, 'native')
    def jansiDir = libraryFactory.makeVersionSpecificDir(nativeDir)
    def library = new File(jansiDir, libraryFactory.create().path)

    def setup() {
        executer.requireGradleDistribution().withNoExplicitTmpDir()
    }

    def "native services libs are unpacked to gradle user home dir"() {
        given:
        executer.withArguments('-q')

        when:
        succeeds("help")

        then:
        nativeDir.directory
    }

    @Issue("GRADLE-3573")
    def "jansi library is unpacked to gradle user home dir and isn't overwritten if existing"() {
        String tmpDirJvmOpt = "-Djava.io.tmpdir=$tmpDir.testDirectory.absolutePath"
        executer.withBuildJvmOpts(tmpDirJvmOpt)

        when:
        succeeds("help")

        then:
        library.exists()
        assertNoFilesInTmp()
        long lastModified = library.lastModified()

        when:
        succeeds("help")

        then:
        library.exists()
        assertNoFilesInTmp()
        lastModified == library.lastModified()
    }

    @Issue("GRADLE-3573")
    def "test workers use a different version of Jansi than initialized by Gradle's native services"() {
        given:
        def jansiVersion = '1.11'

        buildFile << """
            apply plugin: 'java'

            repositories {
                mavenCentral()
            }

            dependencies {
                testCompile 'org.fusesource.jansi:jansi:$jansiVersion'
                testCompile 'junit:junit:4.12'
            }
        """

        file('src/test/java/org/gradle/JansiTest.java') << """
            package org.gradle;

            import org.fusesource.jansi.Ansi;

            import org.junit.Test;
            import static org.junit.Assert.assertNull;
            import static org.junit.Assert.assertEquals;

            public class JansiTest {
                @Test
                public void canUseCustomJansiVersion() {
                    assertNull(System.getProperty("${JANSI_LIBRARY_PATH_SYS_PROP}"));
                    assertEquals(Ansi.class.getPackage().getImplementationVersion(), "$jansiVersion");
                }
            }
        """

        when:
        succeeds('test')

        then:
        executedAndNotSkipped(':test')
    }

    private void assertNoFilesInTmp() {
        assert tmpDir.testDirectory.listFiles().length == 0
    }
}
