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

package org.gradle.java.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class IncrementalJavaCompilationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            allprojects {
                apply plugin: 'java'
                compileJava.options.incremental = true
            }

            task cleanFiles(type: Delete) {
                delete("changedFiles.txt", "unchangedFiles.txt")
            }

            compileJava {
                dependsOn cleanFiles
                def times = [:]
                doFirst {
                    fileTree("build/classes/main").each {
                        if (it.file) {
                            times[it] = it.lastModified()
                        }
                    }
                }
                doLast {
                    sleep(1100) //lastModified granularity
                    def changedFiles = ""
                    def unchangedFiles = ""
                    times.each { k,v ->
                        if (k.lastModified() != v) {
                            changedFiles += k.name + ","
                        } else {
                            unchangedFiles += k.name + ","
                        }
                    }
                    file("changedFiles.txt").text = changedFiles
                    file("unchangedFiles.txt").text = unchangedFiles
                }
            }
        """

        file("src/main/java/org/Person.java") << """package org;
        public interface Person {
            String getName();
        }"""
        file("src/main/java/org/PersonImpl.java") << """package org;
        public class PersonImpl implements Person {
            public String getName() { return "Szczepan"; }
        }"""
        file("src/main/java/org/AnotherPersonImpl.java") << """package org;
        public class AnotherPersonImpl extends PersonImpl {
            public String getName() { return "Szczepan Faber " + WithConst.X; }
        }"""
        file("src/main/java/org/WithConst.java") << """package org;
        public class WithConst {
            final static int X = 100;
        }"""
    }

    Set getChangedFiles() {
        file("changedFiles.txt").text.split(",").findAll { it.length() > 0 }.collect { it.replaceAll("\\.class", "")}
    }

    Set getUnchangedFiles() {
        file("unchangedFiles.txt").text.split(",").findAll { it.length() > 0 }.collect { it.replaceAll("\\.class", "")}
    }

    def "compiles only a single class that was changed"() {
        run "compileJava"

        file("src/main/java/org/AnotherPersonImpl.java").text = """package org;
        public class AnotherPersonImpl implements Person {
            public String getName() { return "Hans"; }
        }"""

        when: run "compileJava"

        then: changedFiles == ['AnotherPersonImpl'] as Set
    }

    def "refreshes the class dependencies with each run"() {
        run "compileJava"

        file("src/main/java/org/AnotherPersonImpl.java").text = """package org;
        public class AnotherPersonImpl {}""" //remove the dependency to the interface

        when: run "compileJava"

        then: changedFiles == ['AnotherPersonImpl'] as Set

        when:
        file("src/main/java/org/Person.java").text = """package org;
        public interface Person {
            String getName();
            String toString();
        }"""
        run "compileJava"

        then: changedFiles == ['PersonImpl', 'Person'] as Set
    }

    def "detects class transitive dependents"() {
        when: run "compileJava"

        then:
        changedFiles.empty
        unchangedFiles.empty

        when:
        file("src/main/java/org/Person.java").text = """package org;
        public interface Person {
            String toString();
        }"""

        run "compileJava"

        then:
        changedFiles == ['AnotherPersonImpl', 'PersonImpl', 'Person'] as Set
    }

    def "is sensitive to deletion and change"() {
        run "compileJava"

        assert file("src/main/java/org/PersonImpl.java").delete()

        file("src/main/java/org/AnotherPersonImpl.java").text = """package org;
        public class AnotherPersonImpl implements Person {
            public String getName() { return "Hans"; }
        }"""

        when: run "compileJava"

        then:
        !file("build/classes/main/org/PersonImpl.class").exists()
        changedFiles == ['AnotherPersonImpl', 'PersonImpl'] as Set
    }

    def "is sensitive to inlined constants"() {
        run "compileJava"

        file("src/main/java/org/WithConst.java").text = """package org;
        public class WithConst {
            static final int X = 20;
        }"""

        when: run "compileJava"

        then:
        unchangedFiles.empty
        changedFiles.containsAll(['WithConst', 'AnotherPersonImpl', 'PersonImpl', 'Person'])
    }

    def "is sensitive to source annotations"() {
        file("src/main/java/org/ClassAnnotation.java").text = """package org; import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME) public @interface ClassAnnotation {}
        """
        file("src/main/java/org/SourceAnnotation.java").text = """package org; import java.lang.annotation.*;
            @Retention(RetentionPolicy.SOURCE) public @interface SourceAnnotation {}
        """
        file("src/main/java/org/UsesClassAnnotation.java").text = """package org;
            @ClassAnnotation public class UsesClassAnnotation {}
        """
        file("src/main/java/org/UsesSourceAnnotation.java").text = """package org;
            @SourceAnnotation public class UsesSourceAnnotation {}
        """
        run "compileJava"

        file("src/main/java/org/ClassAnnotation.java").text = """package org; import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME) public @interface ClassAnnotation {
                String foo() default "foo";
            }"""

        when: run "compileJava"

        then:
        unchangedFiles.empty
        changedFiles.containsAll(['WithConst', 'AnotherPersonImpl', 'PersonImpl', 'Person'])
    }

    def "removal of class causes deletion of inner classes"() {
        file("src/main/java/org/B.java") << """package org;
            public class B {
                public static class InnerB {}
            }
        """

        when: run "compileJava"

        then:
        def classes = [file('build/classes/main/org/B.class'), file('build/classes/main/org/B$InnerB.class')]
        classes.each { assert it.exists() }

        when:
        assert file("src/main/java/org/B.java").delete()
        run "compileJava"

        then:
        classes.each { assert !it.exists() }
    }
}