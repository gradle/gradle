/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule

class SamplesCustomizingDependenciesIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("userguide/dependencies/unresolvedTransitiveDependencies")
    def "reports an error for unresolved transitive dependency artifacts"() {
        executer.inDirectory(sample.dir)

        when:
        fails('compileJava')

        then:
        errorOutput.contains("""Could not resolve all files for configuration ':compileClasspath'.
> Could not find jms.jar (javax.jms:jms:1.1).
  Searched in the following locations:
      https://repo.maven.apache.org/maven2/javax/jms/jms/1.1/jms-1.1.jar
> Could not find jmxtools.jar (com.sun.jdmk:jmxtools:1.2.1).
  Searched in the following locations:
      https://repo.maven.apache.org/maven2/com/sun/jdmk/jmxtools/1.2.1/jmxtools-1.2.1.jar
> Could not find jmxri.jar (com.sun.jmx:jmxri:1.2.1).
  Searched in the following locations:
      https://repo.maven.apache.org/maven2/com/sun/jmx/jmxri/1.2.1/jmxri-1.2.1.jar""")
    }

    @UsesSample("userguide/dependencies/excludingTransitiveDependenciesForDependency")
    def "can exclude transitive dependencies for declared dependency"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('compileJava')

        then:
        sample.dir.file('build/classes/java/main/Main.class').isFile()
    }

    @UsesSample("userguide/dependencies/excludingTransitiveDependenciesForConfiguration")
    def "can exclude transitive dependencies for particular configuration"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('compileJava')

        then:
        sample.dir.file('build/classes/java/main/Main.class').isFile()
    }

    @UsesSample("userguide/dependencies/excludingTransitiveDependenciesForAllConfigurations")
    def "can exclude transitive dependencies for all configurations"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('compileJava')

        then:
        sample.dir.file('build/classes/java/main/Main.class').isFile()
    }

    @UsesSample("userguide/dependencies/forcingDependencyVersion")
    def "can force a dependency version"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('copyLibs')

        then:
        def libs = listFileInBuildLibsDir()
        libs.any { it.name == 'commons-codec-1.9.jar' }
        !libs.any { it.name == 'commons-codec-1.10.jar' }
    }

    @UsesSample("userguide/dependencies/forcingDependencyVersionPerConfiguration")
    def "can force a dependency version for particular configuration"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('copyLibs')

        then:
        def libs = listFileInBuildLibsDir()
        libs.any { it.name == 'commons-codec-1.9.jar' }
        !libs.any { it.name == 'commons-codec-1.10.jar' }
    }

    @UsesSample("userguide/dependencies/resolvingArtifactOnly")
    def "can resolve dependency with artifact-only declaration"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('copyLibs')

        then:
        listFileInBuildLibsDir().any { it.name == 'jquery-3.2.1.js' }
    }

    @UsesSample("userguide/dependencies/resolvingArtifactOnlyWithClassifier")
    def "can resolve dependency with artifact-only declaration with classifier"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('copyLibs')

        then:
        listFileInBuildLibsDir().any { it.name == 'jquery-3.2.1-min.js' }
    }

    private TestFile[] listFileInBuildLibsDir() {
        sample.dir.file('build/libs').listFiles()
    }
}
