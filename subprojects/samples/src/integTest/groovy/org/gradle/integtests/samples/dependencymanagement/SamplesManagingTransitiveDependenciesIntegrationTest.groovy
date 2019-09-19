/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.samples.dependencymanagement

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

@Requires(KOTLIN_SCRIPT)
class SamplesManagingTransitiveDependenciesIntegrationTest extends AbstractIntegrationSpec {

    private static final String COPY_LIBS_TASK_NAME = 'copyLibs'

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    def setup() {
        executer.withRepositoryMirrors()
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/managingTransitiveDependencies/versionsWithConstraints")
    def "respects dependency constraints for direct and transitive dependencies with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        dslDir.file('build/libs/httpclient-4.5.3.jar').isFile()
        dslDir.file('build/libs/commons-codec-1.11.jar').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/managingTransitiveDependencies/unresolved")
    def "reports an error for unresolved transitive dependency artifacts with #dsl dsl"() {
        executer.inDirectory(sample.dir.file(dsl))

        when:
        fails('compileJava')

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compileClasspath'.")
        failure.assertHasCause("""Could not find jms-1.1.jar (javax.jms:jms:1.1).
Searched in the following locations:
    ${RepoScriptBlockUtil.mavenCentralRepositoryMirrorUrl()}javax/jms/jms/1.1/jms-1.1.jar""")
        failure.assertHasCause("""Could not find jmxtools-1.2.1.jar (com.sun.jdmk:jmxtools:1.2.1).
Searched in the following locations:
    ${RepoScriptBlockUtil.mavenCentralRepositoryMirrorUrl()}com/sun/jdmk/jmxtools/1.2.1/jmxtools-1.2.1.jar""")
        failure.assertHasCause("""Could not find jmxri-1.2.1.jar (com.sun.jmx:jmxri:1.2.1).
Searched in the following locations:
    ${RepoScriptBlockUtil.mavenCentralRepositoryMirrorUrl()}com/sun/jmx/jmxri/1.2.1/jmxri-1.2.1.jar""")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/managingTransitiveDependencies/excludeForDependency")
    def "can exclude transitive dependencies for declared dependency for #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('compileJava', COPY_LIBS_TASK_NAME)

        then:
        dslDir.file('build/classes/java/main/Main.class').isFile()
        def libs = listFilesInBuildLibsDir(dslDir)
        libs.size() == 3
        libs.any { it.name == 'log4j-1.2.15.jar' || it.name == 'mail-1.4.jar' || it.name == 'activation-1.1.jar' }

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/managingTransitiveDependencies/excludeForConfiguration")
    def "can exclude transitive dependencies for particular configuration"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('compileJava', COPY_LIBS_TASK_NAME)

        then:
        dslDir.file('build/classes/java/main/Main.class').isFile()
        def libs = listFilesInBuildLibsDir(dslDir)
        libs.size() == 3
        libs.any { it.name == 'log4j-1.2.15.jar' || it.name == 'mail-1.4.jar' || it.name == 'activation-1.1.jar' }

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("userguide/dependencyManagement/managingTransitiveDependencies/excludeForAllConfigurations")
    def "can exclude transitive dependencies for all configurations"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('compileJava', COPY_LIBS_TASK_NAME)

        then:
        sample.dir.file('build/classes/java/main/Main.class').isFile()
        def libs = listFilesInBuildLibsDir(sample.dir)
        libs.size() == 3
        libs.any { it.name == 'log4j-1.2.15.jar' || it.name == 'mail-1.4.jar' || it.name == 'activation-1.1.jar' }
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/managingTransitiveDependencies/forceForDependency")
    def "can force a dependency version for #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        executer.expectDeprecationWarning()
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        def libs = listFilesInBuildLibsDir(dslDir)
        libs.any { it.name == 'commons-codec-1.9.jar' }
        !libs.any { it.name == 'commons-codec-1.10.jar' }

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/managingTransitiveDependencies/forceForConfiguration")
    def "can force a dependency version for particular configuration for #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        def libs = listFilesInBuildLibsDir(dslDir)
        libs.any { it.name == 'commons-codec-1.9.jar' }
        !libs.any { it.name == 'commons-codec-1.10.jar' }

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/managingTransitiveDependencies/disableForDependency")
    def "can disable transitive dependency resolution for dependency for #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        assertSingleLib(dslDir, 'guava-23.0.jar')

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/managingTransitiveDependencies/disableForConfiguration")
    def "can disable transitive dependency resolution for particular configuration for #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        assertSingleLib(dslDir, 'guava-23.0.jar')

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/managingTransitiveDependencies/constraintsFromBOM")
    def "can import dependency versions from a bom for #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        def libs = listFilesInBuildLibsDir(dslDir)
        libs.findAll { it.name == 'gson-2.8.2.jar' || it.name == 'dom4j-1.6.1.jar' || it.name == 'xml-apis-1.4.01.jar'}.size() == 3

        where:
        dsl << ['groovy', 'kotlin']
    }

    private TestFile[] listFilesInBuildLibsDir(TestFile dslDir) {
        dslDir.file('build/libs').listFiles()
    }

    private void assertSingleLib(TestFile dslDir, String filename) {
        def libs = listFilesInBuildLibsDir(dslDir)
        assert libs.size() == 1
        assert libs[0].name == filename
    }
}
