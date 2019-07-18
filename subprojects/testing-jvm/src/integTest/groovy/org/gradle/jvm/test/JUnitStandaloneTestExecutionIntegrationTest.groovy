/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.test

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.hamcrest.CoreMatchers
import spock.lang.Ignore
import spock.lang.Unroll

class JUnitStandaloneTestExecutionIntegrationTest extends AbstractJUnitTestExecutionIntegrationSpec {

    def "creates a JUnit test suite binary"() {
        given:
        applyJUnitPlugin()

        and:
        myTestSuiteSpec()

        when:
        run 'components'

        then:
        noExceptionThrown()

        and:
        outputContains "Test suite 'myTest:binary'"
        outputContains "build using task: :myTestBinary"
    }

    def "fails with a reasonable error when no repository is declared"() {
        given:
        applyJUnitPluginAndDoNotDeclareRepo()

        and:
        testSuiteComponent()

        and:
        passingTestCase()

        when:
        fails ':myTestBinaryTest'

        then:
        failure.assertHasCause "Could not resolve all dependencies for 'Test suite 'myTest:binary'' source set 'Java source 'myTest:java''"
        failure.assertHasCause "Cannot resolve external dependency junit:junit:4.12 because no repositories are defined."
    }

    @Unroll("Fails if no JUnit version is specified for a test suite declared under #container")
    def "fails if no JUnit version is specified"() {
        given:
        applyJUnitPlugin()

        and:
        buildFile << """
            model {
                $container {
                    myTest(JUnitTestSuiteSpec)
                }
            }
        """

        when:
        fails 'components'

        then:
        failure.assertHasCause "Test suite 'myTest' doesn't declare JUnit version. Please specify it with `jUnitVersion '4.12'` for example."

        where:
        container << ['testSuites', 'components']
    }

    @Unroll("Fails if no JUnit version is specified even if found in dependencies for a test suite declared under #container")
    def "fails if no JUnit version is specified even if found in dependencies"() {
        given:
        applyJUnitPlugin()

        and:
        buildFile << """
            model {
                $container {
                    myTest(JUnitTestSuiteSpec) {
                        sources {
                            java {
                                dependencies.module('junit:junit:4.12')
                            }
                        }
                    }
                }
            }
        """

        when:
        fails 'components'

        then:
        failure.assertHasCause "Test suite 'myTest' doesn't declare JUnit version. Please specify it with `jUnitVersion '4.12'` for example."

        where:
        container << ['testSuites', 'components']
    }

    @Unroll("Executes a passing test suite with a JUnit component and #sourceconfig.description")
    def "executes a passing test suite"() {
        given:
        applyJUnitPlugin()
        boolean useLib = sourceconfig.hasLibraryDependency
        boolean useExternalDep = sourceconfig.hasExternalDependency

        and:
        testSuiteComponent(sourceconfig)
        if (useLib) {
            utilsLibrary()
        }

        and:
        standaloneTestCase(true, useLib, useExternalDep)

        when:
        succeeds ':myTestBinaryTest'

        then:
        executedAndNotSkipped ':compileMyTestBinaryMyTestJava', ':myTestBinaryTest'
        int testCount = 1;
        def tests = ['test']
        if (useLib) {
            testCount++
            tests << 'testLibDependency'
        }
        if (useExternalDep) {
            testCount++
            tests << 'testExternalDependency'
        };
        def result = new DefaultTestExecutionResult(testDirectory, 'build', 'myTest')
        result.assertTestClassesExecuted('MyTest')
        def check = result.testClass('MyTest')
            .assertTestCount(testCount, 0, 0)
            .assertTestsExecuted(tests as String[])
        tests.each {
            check.assertTestPassed(it)
        }

        where:
        sourceconfig << SourceSetConfiguration.values()
    }

    @Unroll("Executes a failing test suite with a JUnit component and #sourceconfig.description")
    def "executes a failing test suite"() {
        given:
        applyJUnitPlugin()
        boolean useLib = sourceconfig.hasLibraryDependency
        boolean useExternalDep = sourceconfig.hasExternalDependency

        and:
        testSuiteComponent(sourceconfig)
        if (useLib) {
            utilsLibrary()
        }

        and:
        standaloneTestCase(false, useLib, useExternalDep)

        when:
        fails ':myTestBinaryTest'

        then:
        executedAndNotSkipped ':compileMyTestBinaryMyTestJava', ':myTestBinaryTest'
        failure.assertHasCause('There were failing tests. See the report at')
        int testCount = 1;
        def tests = [
            'test': 'java.lang.AssertionError: expected:<true> but was:<false>',
        ]
        if (useLib) {
            testCount++
            tests.testLibDependency = 'java.lang.AssertionError: expected:<42> but was:<666>'
        }
        if (useExternalDep) {
            testCount++
            tests.testExternalDependency = 'org.junit.ComparisonFailure: expected:<[Hello World]> but was:<[oh noes!]>'
        };
        def result = new DefaultTestExecutionResult(testDirectory, 'build', 'myTest')
        result.assertTestClassesExecuted('MyTest')
        def check = result.testClass('MyTest')
            .assertTestCount(testCount, testCount, 0)
            .assertTestsExecuted(tests.keySet() as String[])
        tests.each { test, error ->
            check.assertTestFailed(test, CoreMatchers.equalTo(error))
        }

        where:
        sourceconfig << SourceSetConfiguration.values()
    }

    @Unroll("Can have multiple JUnit test suites in a single project under #container")
    def "can have multiple JUnit test suites in a single project"() {
        given:
        applyJUnitPlugin()

        def suites = ['myTest', 'myOtherTest']
        suites.each {
            buildFile << """
            model {
                $container {
                    ${it}(JUnitTestSuiteSpec) ${SourceSetConfiguration.NONE.configuration}
                }
            }"""
        }

        and:
        suites.each { name ->
            standaloneTestCase(true, false, false, name)
        }

        when:
        succeeds(suites.collect { ":${it}BinaryTest" } as String[])

        then:
        noExceptionThrown()
        executedAndNotSkipped(suites.collect { ":compile${it.capitalize()}Binary${it.capitalize()}Java" } as String[])
        executedAndNotSkipped(suites.collect { ":${it}BinaryTest" } as String[])

        where:
        container << ['testSuites', 'components']
    }

    def "assemble does not compile nor run test suite"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteComponent()

        and:
        failingTestCase()

        when:
        succeeds 'assemble'

        then:
        result.assertTasksExecuted ':assemble' // only
    }

    def "check executes the test suite"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteComponent()

        and:
        failingTestCase()

        when:
        fails 'check'

        then:
        result.assertTasksExecutedInOrder ':compileMyTestBinaryMyTestJava', ':myTestBinaryTest' // only

        and:
        def result = new DefaultTestExecutionResult(testDirectory, 'build', 'myTest')
        result.assertTestClassesExecuted('MyTest')
        result.testClass('MyTest')
            .assertTestCount(1, 1, 0)
    }

    // for now the build task of a test suite does nothing
    @NotYetImplemented
    def "building the test suite binary does not run test suite"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteComponent()

        and:
        failingTestCase()

        when:
        succeeds 'myTestBinary'

        then:
        result.assertTasksExecutedInOrder ':compileMyTestBinaryMyTestJava', ':myTestBinary' // only
    }

    @NotYetImplemented
    def "should fail if a library attempts to depend on a test suite"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteComponent()

        and:
        buildFile << '''
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    library 'myTest'
                                }
                            }
                        }
                    }
                }
            }
        '''
        file('src/myLib/java/MyLib.java') << 'public class MyLib {}'

        when:
        fails ':myLibJar'

        then:
        failure.assertHasCause "Project ':' does not contain library 'myTest'. Did you want to use 'myLib'?"
    }

    @NotYetImplemented
    def "should fail if a library attempts to depend on a project that only declares a test suite"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteComponent()

        and:
        file('settings.gradle') << 'include "sub"'
        file('sub/build.gradle') << '''
            plugins {
                id 'jvm-component'
                id 'java-lang'
            }

            model {
                components {
                    myLib(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    project ':'
                                }
                            }
                        }
                    }
                }
            }
        '''
        file('sub/src/myLib/java/MyLib.java') << 'public class MyLib {}'

        when:
        fails ':sub:myLibJar'

        then:
        failure.assertHasCause "Project ':' doesn't define any library."
    }

    def "should not allow a test suite to use a non-exported class from a dependency"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteComponent()
        utilsLibrary()

        and:
        file('src/myTest/java/MyTest.java') << """
        import org.junit.Test;

        import static org.junit.Assert.*;

        public class MyTest {

            @Test
            public void test() {
                assertEquals(utils.internal.InternalUtils.MAGIC, 42);
            }
        }
        """.stripMargin()

        when:
        fails ':compileMyTestBinaryMyTestJava'

        then:
        failure.assertHasErrorOutput 'package utils.internal does not exist'
    }

    def "test should access test resources"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteComponent()
        checkResourceProcessTaskType()

        and:
        testCaseReadingResourceFile()
        file('src/myTest/resources/data.properties') << 'magic = 42'

        when:
        succeeds ':myTestBinaryTest'

        then:
        noExceptionThrown()
        executedAndNotSkipped ':compileMyTestBinaryMyTestJava', ':processMyTestBinaryMyTestResources', ':myTestBinaryTest'

        when:
        succeeds ':checkTaskType'

        then:
        noExceptionThrown()
    }

    def "test should access processed test resources in a non conventional place"() {
        given:
        applyJUnitPlugin()

        and:
        buildFile << """
            model {
                testSuites {
                    myTest(JUnitTestSuiteSpec) {
                        jUnitVersion '4.12'
                        sources {
                            resources {
                                source.srcDirs 'src/test-resources'
                            }
                        }
                    }
                }
                tasks.processMyTestBinaryMyTestResources {
                    // uncomment lines
                    filter {
                        it.startsWith('#') ? it.substring(1) : it
                    }
                }
            }
        """

        and:
        testCaseReadingResourceFile()
        file('src/test-resources/data.properties') << '# magic = 42'

        when:
        succeeds ':myTestBinaryTest'

        then:
        noExceptionThrown()
        executedAndNotSkipped ':compileMyTestBinaryMyTestJava', ':processMyTestBinaryMyTestResources', ':myTestBinaryTest'
    }

    @Unroll("Test should execute test suite with dependency on local library #library")
    def "test should execute with transitive dependencies of local libraries"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteWithDependencyOnLocalLibraryWithExternalTransitiveDependency(library)

        when:
        succeeds ':myTestBinaryTest'

        then:
        executedAndNotSkipped ':createUtilsJar', ':myTestBinaryTest'

        where:
        library << ['utils', 'core', 'coreBis']

    }

    def "test should execute with transitive dependencies of a binary specific source set"() {
        given:
        applyJUnitPlugin()

        and:
        buildFile << """
            model {
                components {
                    utils(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    module 'org.apache.commons:commons-lang3:3.4'
                                }
                            }
                        }
                    }
                }
                testSuites {
                    myTest(JUnitTestSuiteSpec) {
                        jUnitVersion '4.12'
                        binaries.all {
                            sources {
                                alternate(JavaSourceSet) {
                                    source.srcDir 'src/alternate/java'
                                    dependencies {
                                        library 'utils'
                                    }
                                }
                            }
                        }
                    }
                }


            }
        """
        file('src/utils/java/Utils.java') << '''import static org.apache.commons.lang3.text.WordUtils.capitalize;
            public class Utils {
                public static String pretty(String str) { return capitalize(str); }
            }
        '''
        file('src/alternate/java/MyTest.java') << """
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class MyTest {
                @Test
                public void test() {
                    assertEquals(Utils.pretty("hello world"), "Hello World");
                }
            }
        """

        when:
        succeeds ':myTestBinaryTest'

        then:
        executedAndNotSkipped ':createUtilsJar', ':myTestBinaryTest'

    }

    def "test should execute with transitive dependencies of a dependency binary specific source set"() {
        given:
        applyJUnitPlugin()

        and:
        buildFile << """
            model {
                components {
                    utils(JvmLibrarySpec) {
                        binaries.all {
                            sources {
                                alternate(JavaSourceSet) {
                                    source.srcDir 'src/alternate/java'
                                    dependencies {
                                        module 'org.apache.commons:commons-lang3:3.4'
                                    }
                                }
                            }
                        }
                    }
                }
                testSuites {
                    myTest(JUnitTestSuiteSpec) {
                        jUnitVersion '4.12'
                        sources {
                            java {
                                dependencies {
                                    library 'utils'
                                }
                            }
                        }
                    }
                }


            }
        """
        file('src/alternate/java/Utils.java') << '''import static org.apache.commons.lang3.text.WordUtils.capitalize;
            public class Utils {
                public static String pretty(String str) { return capitalize(str); }
            }
        '''
        file('src/myTest/java/MyTest.java') << """
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class MyTest {
                @Test
                public void test() {
                    assertEquals(Utils.pretty("hello world"), "Hello World");
                }
            }
        """

        when:
        succeeds ':myTestBinaryTest'

        then:
        executedAndNotSkipped ':createUtilsJar', ':myTestBinaryTest'

    }

    def "runtime transitive dependencies should not be on compile classpath of test suite"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteWithDependencyOnLocalLibraryWithExternalTransitiveDependency('core')

        and:
        file('src/myTest/java/MyTest.java').write """
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class MyTest {
                @Test
                public void test() {
                    assertEquals(Utils.pretty("hello world"), "Hello World");
                }
            }
        """

        when:
        fails ':myTestBinaryTest'

        then:
        failure.assertHasCause 'Compilation failed; see the compiler error output for details'
        failure.assertHasErrorOutput 'variable Utils'
    }

    @Ignore
    def "can customise jUnitVersion per binary"() {
        given:
        applyJUnitPlugin()

        and:
        buildFile << """
            model {
               testSuites {
                    myTest(JUnitTestSuiteSpec) {
                        jUnitVersion '1.618' // great number but bogus JUnit version
                        binaries.all {
                            jUnitVersion '4.12' // correct version
                       }
                    }
                }
            }
        """

        expect:
        succeeds ':myTestBinaryTest'
    }

    private testSuiteWithDependencyOnLocalLibraryWithExternalTransitiveDependency(String dependencyOn='utils') {
        buildFile << """
            model {
                components {
                    core(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    library 'utils'
                                }
                            }
                        }
                    }
                    coreBis(JvmLibrarySpec) {
                        api {
                            dependencies {
                                library 'utils'
                            }
                        }
                    }
                    utils(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    module 'org.apache.commons:commons-lang3:3.4'
                                }
                            }
                        }
                    }
                }
                testSuites {
                    myTest(JUnitTestSuiteSpec) {
                        jUnitVersion '4.12'
                        dependencies {
                            sources {
                                java {
                                    library '$dependencyOn'
                                }
                            }
                        }
                    }
                }
            }
        """
        file('src/core/java/Core.java') << '''
            public class Core {
                public static String pretty(String str) { return Utils.pretty(str); }
            }
        '''
        file('src/coreBis/java/CoreBis.java') << '''
            public class CoreBis extends Utils {
                public static String pretty(String str) { return Utils.pretty(str); }
            }
        '''
        file('src/utils/java/Utils.java') << '''import static org.apache.commons.lang3.text.WordUtils.capitalize;
            public class Utils {
                public static String pretty(String str) { return capitalize(str); }
            }
        '''
        file('src/myTest/java/MyTest.java') << """
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class MyTest {
                @Test
                public void test() {
                    assertEquals(${dependencyOn.capitalize()}.pretty("hello world"), "Hello World");
                }
            }
        """
    }

    private void testCaseReadingResourceFile() {
        file('src/myTest/java/MyTest.java') << """
        import org.junit.Test;
        import java.util.Properties;
        import java.io.InputStream;
        import static org.junit.Assert.*;

        public class MyTest {
            public static String MAGIC;

            static {
                try {
                   Properties properties = new Properties();
                   InputStream in = MyTest.class.getResourceAsStream("data.properties");
                   properties.load(in);
                   MAGIC = properties.getProperty("magic");
                   in.close();
                } catch (Throwable e) {
                    throw new RuntimeException("Test resource not found",e);
                }
            }

            @Test
            public void test() {
                assertEquals(MAGIC, "42");
            }
        }
        """.stripMargin()
    }

    private void checkResourceProcessTaskType() {
        buildFile << '''
            model {
                tasks {
                    create('checkTaskType') {
                        doLast {
                            def processResources = $.tasks.processMyTestBinaryMyTestResources
                            assert processResources instanceof ProcessResources
                        }
                    }
                }
            }
        '''
    }

    private void applyJUnitPluginAndDoNotDeclareRepo() {
        applyJUnitPlugin(false)
    }

    private enum SourceSetConfiguration {
        NONE('no source set is declared', false, false, '{ jUnitVersion "4.12" }'),
        EXPLICIT_NO_DEPS('an explicit source set configuration is used', false, false, '''{
                        jUnitVersion '4.12'
                        sources {
                            java {
                               source.srcDirs 'src/myTest/java'
                            }
                        }
                    }'''),
        LIBRARY_DEP('a dependency onto a local library', true, false, '''{
                        jUnitVersion '4.12'
                        sources {
                            java {
                                dependencies {
                                    library 'utils'
                                }
                            }
                        }
                    }'''),
        EXTERNAL_DEP('a dependency onto an external library', false, true, '''{
                        jUnitVersion '4.12'
                        sources {
                            java {
                                dependencies {
                                    module 'org.apache.commons:commons-lang3:3.4'
                                }
                            }
                        }
                    }'''),
        SUITE_WIDE_EXTERNAL_DEP('a suite-wide dependency onto an external library', false, true, '''{
                        jUnitVersion '4.12'
                        dependencies {
                            module 'org.apache.commons:commons-lang3:3.4'
                        }
                    }''')
        private final String description
        private final String configuration
        private boolean hasLibraryDependency;
        private boolean hasExternalDependency;

        public SourceSetConfiguration(String description, boolean hasLibraryDependency, boolean hasExternalDependency, String configuration) {
            this.description = description
            this.hasLibraryDependency = hasLibraryDependency
            this.hasExternalDependency = hasExternalDependency
            this.configuration = configuration
        }
    }

    private void testSuiteComponent(SourceSetConfiguration config = SourceSetConfiguration.EXPLICIT_NO_DEPS) {
        buildFile << """
            model {
                testSuites {
                    myTest(JUnitTestSuiteSpec) ${config.configuration}
                }
            }
        """
    }

    private void utilsLibrary() {
        buildFile << """
            model {
                components {
                    utils(JvmLibrarySpec) {
                        api {
                            exports 'utils'
                        }
                    }
                }
            }
        """.stripMargin()
        file('src/utils/java/utils/Utils.java') << '''package utils;

        public class Utils {
            public static final int MAGIC = 42;
        }'''.stripMargin()

        file('src/utils/java/utils/internal/InternalUtils.java') << '''package utils.internal;

        public class InternalUtils {
            public static final int MAGIC = 42;
        }'''.stripMargin()
    }

    private void passingTestCase() {
        standaloneTestCase(true, false, false)
    }

    private void failingTestCase() {
        standaloneTestCase(false, false, false)
    }

    private void standaloneTestCase(boolean passing, boolean hasLibraryDependency, boolean hasExternalDependency, String name='myTest') {

        String libraryDependencyTest = """
            @Test
            public void testLibDependency() {
                assertEquals(utils.Utils.MAGIC, ${passing ? '42' : '666'});
            }
        """

        String externalDependencyTest = """
            @Test
            public void testExternalDependency() {
                assertEquals("Hello World", ${passing ? 'org.apache.commons.lang3.text.WordUtils.capitalize("hello world")' : '"oh noes!"'});
            }
        """

        file("src/$name/java/MyTest.java") << """
        import org.junit.Test;

        import static org.junit.Assert.*;

        public class MyTest {

            @Test
            public void test() {
                assertEquals(true, ${passing ? 'true' : 'false'});
            }

            ${hasLibraryDependency ? libraryDependencyTest : ''}

            ${hasExternalDependency ? externalDependencyTest : ''}
        }
        """.stripMargin()
    }
}
