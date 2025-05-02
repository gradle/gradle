/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.fixture

import org.gradle.test.fixtures.file.TestFile


trait JUnitPlatformTestFixture {
    List<TestClass> testClasses = []
    List<TestSuite> testSuites = []

    abstract TestFile getProjectDir()

    TestClass testClass(String name) {
        def testClass = new TestClass(name, projectDir)
        testClasses.add(testClass)
        return testClass
    }

    TestSuite testSuite(String name) {
        def testSuite = new TestSuite(name, this)
        testSuites.add(testSuite)
        return testSuite
    }

    void writeTestClassFiles() {
        testSuites.each { it.writeContents() }
        testClasses.each { it.writeContents() }
    }

    static class TestSuite {
        final String name
        final JUnitPlatformTestFixture fixture

        Set<TestClass> selected = [] as LinkedHashSet

        TestSuite(String name, JUnitPlatformTestFixture fixture) {
            this.name = name
            this.fixture = fixture
        }

        TestClass testClass(String name) {
            def testClass = fixture.testClass(name)
            selected.add(testClass)
            return testClass
        }

        TestSuite writeContents() {
            def classFile = fixture.projectDir.createFile("src/test/java/${name}.java")
            classFile.parentFile.mkdirs()
            classFile << """
                import org.junit.platform.suite.api.SelectClasses;
                import org.junit.platform.suite.api.Suite;

                @Suite
                @SelectClasses({ ${testClassList} })
                public class TestSuite { }
            """.stripIndent()
            return this
        }

        String getTestClassList() {
            selected.collect { "${it.name}.class" }.join(", ")
        }
    }

    static class TestClass {
        final String name
        final TestFile projectDir

        Set<TestMethod> methods = [] as LinkedHashSet
        boolean disabled

        TestClass(String name, TestFile projectDir) {
            this.name = name
            this.projectDir = projectDir
        }

        TestMethod testMethod(String name) {
            def method = new TestMethod(name)
            methods.add(method)
            return method
        }

        TestMethod parameterizedMethod(String name, Closure config = {}) {
            def method = new ParameterizedTestMethod(name)
            methods.add(method)
            method.with(config)
            return method
        }

        TestClass disabled() {
            disabled = true
            return this
        }

        TestClass writeContents() {
            def classFile = projectDir.createFile("src/test/java/${name}.java")
            classFile.parentFile.mkdirs()
            classFile << """
                import org.junit.jupiter.api.Disabled;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.params.ParameterizedTest;
                import org.junit.jupiter.params.provider.ValueSource;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                ${disabled ? "@Disabled"  : ""}
                public class ${name} {
                    ${methodContents}
                }
            """.stripIndent()
            return this
        }

        String getMethodContents() {
            methods.collect { method -> method.content }.join("\n")
        }
    }

    static class TestMethod {
        final String name
        boolean disabled
        boolean shouldFail
        String customContent = ""

        TestMethod(String name) {
            this.name = name
        }

        TestMethod disabled() {
            disabled = true
            return this
        }

        TestMethod shouldFail() {
            shouldFail = true
            return this
        }

        TestMethod customContent(String customContent) {
            this.customContent = customContent
            return this
        }

        String getContent() {
            return """
                ${testAnnotation}
                ${disabled ? '@Disabled' : ''}
                ${additionalAnnotations}
                void ${name}(${parameters}) {
                    ${customContent}
                    assertTrue(${shouldFail ? 'false' : 'true'});
                }
            """.stripIndent()
        }

        String getTestAnnotation() {
            return "@Test"
        }

        String getAdditionalAnnotations() {
            return ""
        }

        String getParameters() {
            return ""
        }
    }

    static class ParameterizedTestMethod extends TestMethod {
        ParameterizedTestMethod(String name) {
            super(name)
        }

        @Override
        String getTestAnnotation() {
            return "@ParameterizedTest"
        }

        @Override
        String getAdditionalAnnotations() {
            return '@ValueSource(strings = {"first", "second"})'
        }

        @Override
        String getParameters() {
            return "String testParam"
        }
    }
}
