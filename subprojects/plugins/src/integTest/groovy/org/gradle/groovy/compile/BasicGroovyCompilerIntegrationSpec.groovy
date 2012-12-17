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

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.util.VersionNumber
import org.junit.Rule

import com.google.common.collect.Ordering

@TargetVersions(['1.5.8', '1.6.9', '1.7.11', '1.8.8', '2.0.5'])
abstract class BasicGroovyCompilerIntegrationSpec extends MultiVersionIntegrationSpec {
    @Rule TestResources resources = new TestResources()

    String groovyDependency = "org.codehaus.groovy:groovy-all:$version"

    def setup() {
        executer.withArguments("-i")
    }

    def "compileGoodCode"() {
        groovyDependency = dependency

        expect:
        succeeds("compileGroovy")
        !errorOutput
        file("build/classes/main/Person.class").exists()
        file("build/classes/main/Address.class").exists()

        where:
        dependency << ["org.codehaus.groovy:groovy-all:$version", "org.codehaus.groovy:groovy:$version"]
    }

    def "compileBadCode"() {
        expect:
        fails("compileGroovy")
        // for some reasons, line breaks occur in different places when running this
        // test in different environments; hence we only check for short snippets
        compileErrorOutput.contains 'unable'
        compileErrorOutput.contains 'resolve'
        compileErrorOutput.contains 'Unknown1'
        compileErrorOutput.contains 'Unknown2'
        failure.assertHasCause(compilationFailureMessage)
    }

    def "compileBadJavaCode"() {
        expect:
        fails("compileGroovy")
        compileErrorOutput.contains 'illegal start of type'
        failure.assertHasCause(compilationFailureMessage)
    }

    def "canCompileAgainstGroovyClassThatDependsOnExternalClass"() {
        if (getClass() == AntInProcessGroovyCompilerIntegrationTest &&
                (version == '1.7.11' || versionNumber >= VersionNumber.parse('1.8.7'))) {
            return // known not to work; see comment on GRADLE-2404
        }

        expect:
        succeeds("test")
    }

    def "canListSourceFiles"() {
        expect:
        succeeds("compileGroovy")
        output.contains(new File("src/main/groovy/compile/test/Person.groovy").toString())
        output.contains(new File("src/main/groovy/compile/test/Person2.groovy").toString())
        !errorOutput
    }

    protected ExecutionResult run(String... tasks) {
        configureGroovy()
        super.run(tasks)
    }

    protected ExecutionFailure runAndFail(String... tasks) {
        configureGroovy()
        super.runAndFail(tasks)
    }

    protected ExecutionResult succeeds(String... tasks) {
        configureGroovy()
        super.succeeds(tasks)
    }

    protected ExecutionFailure fails(String... tasks) {
        configureGroovy()
        super.fails(tasks)
    }

    private void configureGroovy() {
        buildFile << """
dependencies { compile '$groovyDependency' }
        """

        buildFile << """
DeprecationLogger.whileDisabled {
    ${compilerConfiguration()}
}
        """
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
