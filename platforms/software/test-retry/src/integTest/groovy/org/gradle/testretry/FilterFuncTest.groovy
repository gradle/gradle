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
package org.gradle.testretry

import javax.annotation.Nullable

class FilterFuncTest extends AbstractGeneralPluginFuncTest {

    def "can filter what is retried (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry {
                maxRetries = 2
                filter {
                    includeClasses.add("*Included*")
                    includeAnnotationClasses.add("*Included*")
                    excludeClasses.add("*Excluded*")
                    excludeAnnotationClasses.add("*Excluded*")
                }
            }
        """

        and:
        inheritedAnnotation("InheritedIncludedAnnotation")
        inheritedAnnotation("InheritedExcludedAnnotation")
        nonInheritedAnnotation("NonInheritedIncludedAnnotation")
        nonInheritedAnnotation("NonInheritedExcludedAnnotation")

        and:
        def noRetry = []
        def shouldRetry = []

        shouldRetry << test("BaseIncludedTest", null, "InheritedIncludedAnnotation")
        noRetry << test("BaseIncludedAndExcludedTest", "BaseIncludedTest", "InheritedExcludedAnnotation")
        noRetry << test("IncludedAndExcludedInheritedTest", "BaseIncludedAndExcludedTest")
        shouldRetry << test("IncludedNonInheritedTest", null, "NonInheritedIncludedAnnotation")
        noRetry << test("StandaloneExcludedTest", null, "NonInheritedIncludedAnnotation")
        noRetry << test("IncludeWithBadAnnotation", null, "InheritedExcludedAnnotation")

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        noRetry.each { testName ->
            with(result.output) {
                it.count("${testName} > flakyTest FAILED") == 1
                it.count("${testName} > flakyTest PASSED") == 0
            }
        }
        shouldRetry.each { testName ->
            with(result.output) {
                it.count("${testName} > flakyTest FAILED") == 2
                it.count("${testName} > flakyTest PASSED") == 1
            }
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "annotation can be inherited from classpath"() {
        given:
        settingsFile << """
            include "lib"
        """

        file("lib/build.gradle") << """
            plugins { id "java" }
        """

        buildFile << """
            dependencies {
                testImplementation(project(":lib"))
            }

            test.retry {
                maxRetries = 2
                filter {
                    excludeAnnotationClasses.add("*Excluded*")
                }
            }
        """

        and:
        file("lib/src/main/java/InheritedExcludedAnnotation.java") << """
            package acme;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            import java.lang.annotation.Inherited;

            @Retention(RetentionPolicy.RUNTIME)
            @Target({ ElementType.TYPE })
            @Inherited
            public @interface InheritedExcludedAnnotation { }
        """

        file("lib/src/main/java/acme/BaseTest.java") << """
            package acme;
            @InheritedExcludedAnnotation
            public class BaseTest {

            }
        """

        and:
        def noRetry = []
        noRetry << test("ExcludedTest", "BaseTest",)

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        with(result.output) {
            it.count("ExcludedTest > flakyTest FAILED") == 1
            it.count("ExcludedTest > flakyTest PASSED") == 0
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    private void nonInheritedAnnotation(String name) {
        file("src/test/java/acme/${name}.java") << """
            package acme;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target({ ElementType.TYPE })
            public @interface $name { }
        """
    }

    private void inheritedAnnotation(String name, String project = "") {
        file("${project ? project + "/" : ""}src/test/java/${name}.java") << """
            package acme;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            import java.lang.annotation.Inherited;

            @Retention(RetentionPolicy.RUNTIME)
            @Target({ ElementType.TYPE })
            @Inherited
            public @interface $name { }
        """
    }

    private String test(String name, @Nullable String superClass, String... annotations) {
        file("src/test/java/acme/${name}.java") << """
            package acme;
            ${annotations.collect { "@$it" }.join("\n")}
            public class $name ${superClass ? " extends $superClass" : ""} {
                @org.junit.Test
                public void flakyTest() {
                    ${flakyAssert(name, 2)}
                }
            }
        """
        return name
    }
}
