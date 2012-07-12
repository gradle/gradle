/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.groovy.compile

import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.ExecutionFailure
import com.google.common.collect.Ordering

@TargetVersions(['1.5.8', '1.6.9', '1.7.10', '1.8.6', '2.0.0'])
abstract class BasicGroovyCompilerIntegrationSpec extends MultiVersionIntegrationSpec {
    @Rule TestResources resources = new TestResources()

    def setup() {
        executer.withArguments("-i")
    }

    def "badCodeBreaksBuild"() {
        when:
        runAndFail("classes")

        then:
        // for some reasons, line breaks occur in different places when running this
        // test in different environments; hence we only check for short snippets
        compileErrorOutput.contains 'unable'
        compileErrorOutput.contains 'resolve'
        compileErrorOutput.contains 'Unknown1'
        compileErrorOutput.contains 'Unknown2'
        failure.assertHasCause(compilationFailureMessage)
    }

    def "badJavaCodeBreaksBuild"() {
        when:
        runAndFail("classes")

        then:
        compileErrorOutput.contains 'illegal start of type'
        failure.assertHasCause(compilationFailureMessage)
    }

    def "canCompileAgainstGroovyClassThatDependsOnExternalClass"() {
        when:
        run("test")

        then:
        noExceptionThrown()
    }

    def "canListSourceFiles"() {
        when:
        run("compileGroovy")

        then:
        output.contains(new File("src/main/groovy/compile/test/Person.groovy").toString())
        output.contains(new File("src/main/groovy/compile/test/Person2.groovy").toString())
        !errorOutput
        file("build/classes/main/compile/test/Person.class").exists()
        file("build/classes/main/compile/test/Person2.class").exists()
    }

    @Override
    protected ExecutionResult run(String... tasks) {
        tweakBuildFile()
        return super.run(tasks)
    }

    @Override
    protected ExecutionFailure runAndFail(String... tasks) {
        tweakBuildFile()
        return super.runAndFail(tasks)
    }

    private void tweakBuildFile() {
        if (version == "2.0.0") {
            // groovy-all-2.0.0 isn't compatible with JDK 1.5
            // see GROOVY-5591
            buildFile << """
dependencies {
    groovy 'org.codehaus.groovy:groovy:$version'
    groovy 'org.codehaus.groovy:groovy-test:$version'
}
            """
        } else {
            buildFile << """
dependencies { groovy 'org.codehaus.groovy:groovy-all:$version' }
            """
        }

        buildFile << compilerConfiguration()
        println "->> USING BUILD FILE: ${buildFile.text}"
    }

    abstract String compilerConfiguration()

    String getCompilationFailureMessage() {
        return "Compilation failed; see the compiler error output for details."
    }

    String getCompileErrorOutput() {
        return errorOutput
    }

    boolean versionLowerThan(String other) {
        compareToVersion(other) < 0
    }

    int compareToVersion(String other) {
        def versionParts = version.split("\\.") as List
        def otherParts = other.split("\\.") as List
        def ordering = Ordering.<Integer>natural().lexicographical()
        ordering.compare(versionParts, otherParts)
    }
}
