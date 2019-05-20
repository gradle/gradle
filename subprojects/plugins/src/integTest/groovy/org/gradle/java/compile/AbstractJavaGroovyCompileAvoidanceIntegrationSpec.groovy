/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.language.fixtures.HelperProcessorFixture
import spock.lang.Issue
import spock.lang.Unroll

abstract class AbstractJavaGroovyCompileAvoidanceIntegrationSpec extends AbstractIntegrationSpec {
    enum Language {
        JAVA,
        GROOVY;

        String getName() {
            return name().toLowerCase()
        }

        String getCompileTaskName() {
            return "compile${name.capitalize()}"
        }
    }

    abstract boolean isUseJar()

    abstract boolean isIncremental()

    def beforeEach(Language language) {
        settingsFile << """
include 'a', 'b'
"""
        buildFile << """
            allprojects {
                apply plugin: '${language.name}'
                task emptyDirs(type: Sync) {
                    into 'build/empty-dirs'
                    from 'src/empty-dirs'
                    includeEmptyDirs = true
                }
            }
        """

        if(language == Language.GROOVY) {
           buildFile << """
            allprojects {
                dependencies {
                    compile localGroovy()
                }
            }
"""
        }

        if(isUseJar()) {
            useJar()
        } else {
            useClassesDir(language)
        }

        if(!isIncremental()) {
            deactivateIncrementalCompile()
        }
    }

    def deactivateIncrementalCompile() {
        buildFile << """
            allprojects {
                tasks.withType(JavaCompile) {
                    options.incremental = false
                }
            }
        """
    }

    def useJar() {
        buildFile << """
            allprojects {
                jar {
                    from emptyDirs
                }
            }
"""
    }

    def useClassesDir(Language language) {
        buildFile << """import static org.gradle.api.attributes.Usage.*;
            allprojects {
                configurations.apiElements.outgoing.variants {
                    classes {
                        attributes.attribute(USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API_CLASSES))
                        artifact file: ${language.compileTaskName}.destinationDir, builtBy: ${language.compileTaskName} 
                        artifact file: emptyDirs.destinationDir, builtBy: emptyDirs
                        artifact file: processResources.destinationDir, builtBy: processResources
                    }
                }
            }
        """
    }

    List<Language> getSupportedLanguages() {
       return [Language.JAVA]
    }

    @Unroll
    def "doesn't recompile when private element of implementation class changes for #language"() {
        given:
        beforeEach(language)
        buildFile << """
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public class ToolImpl { 
                private String thing() { return null; }
                private ToolImpl t = this;
            }
        """
        file("b/src/main/${language.name}/Main.${language.name}") << """
            public class Main { ToolImpl t = new ToolImpl(); }
        """

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // change signatures
        sourceFile.text = """
            public class ToolImpl { 
                private Number thing() { return null; }
                private Object t = this;
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        // add private elements
        sourceFile.text = """
            public class ToolImpl { 
                private Number thing() { return null; }
                private Object t = this;
                private static void someMethod() { }
                private String s;
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        // remove private elements
        sourceFile.text = """
            public class ToolImpl { 
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        // add public method, should change
        sourceFile.text = """
            public class ToolImpl { 
                public void execute() { String s = toString(); } 
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}", ":b:${language.compileTaskName}"

        when:
        // add public field, should change
        sourceFile.text = """
            public class ToolImpl { 
                public static ToolImpl instance; 
                public void execute() { String s = toString(); } 
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}", ":b:${language.compileTaskName}"

        when:
        // add public constructor to replace the default, should not change
        sourceFile.text = """
            public class ToolImpl { 
                public ToolImpl() { }
                public static ToolImpl instance; 
                public void execute() { String s = toString(); } 
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        // add public constructor, should change
        sourceFile.text = """
            public class ToolImpl { 
                public ToolImpl() { }
                public ToolImpl(String s) { }
                public static ToolImpl instance; 
                public void execute() { String s = toString(); } 
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}", ":b:${language.compileTaskName}"

        where:
        language << getSupportedLanguages()
    }


    @Unroll
    def "doesn't recompile when comments and whitespace of implementation class changes"() {
        given:
        beforeEach(language)
        buildFile << """
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public class ToolImpl { }
        """
        file("b/src/main/${language.name}/Main.${language.name}") << """
            public class Main { ToolImpl t = new ToolImpl(); }
        """

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // add comments, change whitespace
        sourceFile.text = """
/**
 * A thing
 */
public class ToolImpl {
    // TODO - add some stuff
}
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        where:
        language << getSupportedLanguages()
    }

    @Unroll
    def "doesn't recompile when implementation class code changes"() {
        given:
        beforeEach(language)
        buildFile << """
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public class ToolImpl {
                public Object s = String.valueOf(12);
                public void execute() { int i = 12; }
            }
        """
        file("b/src/main/${language.name}/Main.${language.name}") << """
            public class Main { ToolImpl t = new ToolImpl(); }
        """

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // change method body and field initializer
        sourceFile.text = """
            public class ToolImpl {
                public Object s = "12";
                public void execute() { String s = toString(); }
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        // add static initializer and constructor
        sourceFile.text = """
            public class ToolImpl {
                static { }
                public ToolImpl() { }
                public Object s = "12";
                public void execute() { String s = toString(); }
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        // change static initializer and constructor
        sourceFile.text = """
            public class ToolImpl {
                static { int i = 123; }
                public ToolImpl() { System.out.println("created!"); }
                public Object s = "12";
                public void execute() { String s = toString(); }
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        where:
        language << getSupportedLanguages()
    }

    @Unroll
    def "recompiles when type of implementation class changes"() {
        given:
        beforeEach(language)
        buildFile << """
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/org/ToolImpl.${language.name}")
        sourceFile << """
            package org;
            public class ToolImpl { void m() { } }
        """
        file("b/src/main/${language.name}/org/Main.${language.name}") << """
            package org;
            public class Main { void go(ToolImpl t) { t.m(); } }
        """

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // change to interface
        sourceFile.text = """
            package org;
            public interface ToolImpl { void m(); }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // change to visibility
        sourceFile.text = """
            package org;
            interface ToolImpl { void m(); }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // change to interfaces
        sourceFile.text = """
            package org;
            interface ToolImpl extends Runnable { void m(); }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        where:
        language << getSupportedLanguages()
    }

    @Unroll
    def "recompiles when constant value of API changes"() {
        given:
        beforeEach(language)
        buildFile << """
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public class ToolImpl { public static final int CONST = 1; }
        """
        file("b/src/main/${language.name}/Main.${language.name}") << """
            public class Main { public static final int CONST2 = 1 + ToolImpl.CONST; }
        """

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // change to constant value
        sourceFile.text = """
            public class ToolImpl { public static final int CONST = 10; }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        where:
        language << getSupportedLanguages()
    }

    @Unroll
    def "recompiles when generic type signatures of implementation class changes"() {
        given:
        beforeEach(language)
        buildFile << """
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public interface ToolImpl { void m(java.util.List<String> s); }
        """
        file("b/src/main/${language.name}/Main.${language.name}") << """
            public class Main { void go(ToolImpl t) { t.m(null); } }
        """

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // add type parameters to interface
        sourceFile.text = """
            public interface ToolImpl<T> { void m(java.util.List<String> s); }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // add type parameters to method
        sourceFile.text = """
            public interface ToolImpl<T> { <S extends T> void m(java.util.List<S> s); }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // change type parameters on interface
        sourceFile.text = """
            public interface ToolImpl<T extends CharSequence> { <S extends T> void m(java.util.List<S> s); }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // change type parameters on method
        sourceFile.text = """
            public interface ToolImpl<T extends CharSequence> { <S extends Number> void m(java.util.List<S> s); }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        where:
        language << getSupportedLanguages()
    }

    @Unroll
    def "doesn't recompile when private inner class changes"() {
        given:
        beforeEach(language)
        buildFile << """
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public class ToolImpl {
                private class Thing { }
            }
        """
        file("b/src/main/${language.name}/Main.${language.name}") << """
            public class Main { ToolImpl t = new ToolImpl(); }
        """

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // ABI change of inner class
        sourceFile.text = """
            public class ToolImpl {
                private class Thing {
                    public long v;
                }
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        // Remove inner class
        sourceFile.text = """
            public class ToolImpl {
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        // Anonymous class
        sourceFile.text = """
            public class ToolImpl {
                private Object r = new Runnable() { public void run() { } };
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        // Add classes
        sourceFile.text = """
            public class ToolImpl {
                private Object r = new Runnable() {
                    public void run() {
                        class LocalThing { }
                    }
                };
                private static class C1 {
                }
                private class C2 {
                    public void go() {
                        Object r2 = new Runnable() { public void run() { } };
                    }
                }
            }
"""

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        where:
        language << getSupportedLanguages()
    }

    @Unroll
    def "doesn't recompile when implementation resource is changed in various ways"() {
        given:
        beforeEach(language)
        buildFile << """
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public class ToolImpl { public void execute() { int i = 12; } }
        """
        def resourceFile = file("a/src/main/resources/a.properties")
        resourceFile.text = "a = 12"
        file("b/src/main/${language.name}/Main.${language.name}") << """
            public class Main { ToolImpl t = new ToolImpl(); }
        """

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        resourceFile.text = "a = 11"

        then:
        succeeds ":b:${language.compileTaskName}"
        skipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        resourceFile.delete()

        then:
        succeeds ":b:${language.compileTaskName}"
        skipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        file("a/src/main/resources/org/gradle/b.properties").createFile()

        then:
        succeeds ":b:${language.compileTaskName}"
        skipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        where:
        language << getSupportedLanguages()
    }

    @Unroll
    def "doesn't recompile when empty directories are changed in various ways"() {
        given:
        beforeEach(language)
        buildFile << """
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public class ToolImpl { public void execute() { int i = 12; } }
        """
        file("b/src/main/${language.name}/Main.${language.name}") << """
            public class Main { ToolImpl t = new ToolImpl(); }
        """
        file("a/src/empty-dirs/ignore-me.txt").createFile()
        file("a/src/empty-dirs/a/dir").mkdirs()

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        file("a/src/empty-dirs/a/dir2").mkdirs()

        then:
        succeeds ":b:${language.compileTaskName}"
        skipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        file("a/src/empty-dirs/a/dir").deleteDir()

        then:
        succeeds ":b:${language.compileTaskName}"
        skipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        where:
        language << getSupportedLanguages()
    }

    @Unroll
    def "recompiles source when annotation processor implementation on annotation processor classpath changes"() {
        given:
        beforeEach(language)
        settingsFile << "include 'c'"

        buildFile << """
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
            project(':c') {
                configurations {
                    processor
                }
                dependencies {
                    compile project(':a')
                    processor project(':b')
                }
                ${language.compileTaskName}.options.annotationProcessorPath = configurations.processor
                task run(type: JavaExec) {
                    main = 'TestApp'
                    classpath = sourceSets.main.runtimeClasspath
                }
            }
        """

        def fixture = new HelperProcessorFixture()

        // The annotation
        fixture.writeApiTo(file("a"))

        // The processor and library
        fixture.writeSupportLibraryTo(file("b"))
        fixture.writeAnnotationProcessorTo(file("b"))

        // The class that is the target of the processor
        file("c/src/main/${language.name}/TestApp.${language.name}") << '''
            @Helper
            class TestApp {
                public static void main(String[] args) {
                    System.out.println(new TestAppHelper().getValue()); // generated class
                }
            }
'''

        when:
        run(':c:run')

        then:
        executedAndNotSkipped(":a:${language.compileTaskName}")
        executedAndNotSkipped(":b:${language.compileTaskName}")
        executedAndNotSkipped(":c:${language.compileTaskName}")
        outputContains('greetings')

        when:
        run(':c:run')

        then:
        skipped(":a:${language.compileTaskName}")
        skipped(":b:${language.compileTaskName}")
        skipped(":c:${language.compileTaskName}")
        outputContains('greetings')

        when:
        // Update the library class
        fixture.message = 'hello'
        fixture.writeSupportLibraryTo(file("b"))

        run(':c:run')

        then:
        skipped(":a:${language.compileTaskName}")
        executedAndNotSkipped(":b:${language.compileTaskName}")
        executedAndNotSkipped(":c:${language.compileTaskName}")
        outputContains('hello')

        when:
        run(':c:run')

        then:
        skipped(":a:${language.compileTaskName}")
        skipped(":b:${language.compileTaskName}")
        skipped(":c:${language.compileTaskName}")
        outputContains('hello')

        when:
        // Update the processor class
        fixture.suffix = 'world'
        fixture.writeAnnotationProcessorTo(file("b"))

        run(':c:run')

        then:
        skipped(":a:${language.compileTaskName}")
        executedAndNotSkipped(":b:${language.compileTaskName}")
        executedAndNotSkipped(":c:${language.compileTaskName}")
        outputContains('hello world')

        where:
        language << getSupportedLanguages()
    }

    @Unroll
    def "ignores annotation processor implementation when included in the compile classpath but annotation processing is disabled"() {
        given:
        beforeEach(language)
        settingsFile << "include 'c'"

        buildFile << """
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
            project(':c') {
                dependencies {
                    compile project(':b')
                }
                ${language.compileTaskName}.options.annotationProcessorPath = files()
            }
        """

        def fixture = new HelperProcessorFixture()

        fixture.writeSupportLibraryTo(file("a"))
        fixture.writeApiTo(file("b"))
        fixture.writeAnnotationProcessorTo(file("b"))

        file("c/src/main/${language.name}/TestApp.${language.name}") << '''
            @Helper
            class TestApp {
                public static void main(String[] args) {
                }
            }
'''

        when:
        run(":c:${language.compileTaskName}")

        then:
        executedAndNotSkipped(":a:${language.compileTaskName}")
        executedAndNotSkipped(":b:${language.compileTaskName}")
        executedAndNotSkipped(":c:${language.compileTaskName}")

        when:
        run(":c:${language.compileTaskName}")

        then:
        skipped(":a:${language.compileTaskName}")
        skipped(":b:${language.compileTaskName}")
        skipped(":c:${language.compileTaskName}")

        when:
        // Update the library class
        fixture.message = 'hello'
        fixture.writeSupportLibraryTo(file("a"))

        run(":c:${language.compileTaskName}")

        then:
        executedAndNotSkipped(":a:${language.compileTaskName}")
        skipped(":b:${language.compileTaskName}")
        skipped(":c:${language.compileTaskName}")

        when:
        // Update the processor class
        fixture.suffix = 'world'
        fixture.writeAnnotationProcessorTo(file("b"))

        run(":c:${language.compileTaskName}")

        then:
        skipped(":a:${language.compileTaskName}")
        executedAndNotSkipped(":b:${language.compileTaskName}")
        skipped(":c:${language.compileTaskName}")

        where:
        language << getSupportedLanguages()
    }

    @Unroll
    def "change to transitive super-class in different project should trigger recompilation"() {
        given:
        beforeEach(language)
        settingsFile << "include 'c'"

        buildFile << """
            project(':a') {
                dependencies {
                    compile project(':b')
                }
            }
            project(':b') {
                dependencies {
                    compile project(':c')
                }
            }
        """

        file("a/src/main/${language.name}/A.${language.name}") << "public class A extends B { void a() { b(); String c = c(); } }"
        file("b/src/main/${language.name}/B.${language.name}") << "public class B extends C { void b() { d(); } }"
        file("c/src/main/${language.name}/C.${language.name}") << "public class C { String c() { return null; }; void d() {} }"

        when:
        succeeds ":a:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"
        executedAndNotSkipped ":c:${language.compileTaskName}"

        when:
        file("c/src/main/${language.name}/C.${language.name}").text = "public class C { void c() {}; void d() {} }"

        then:
        fails ":a:${language.compileTaskName}"
        failure.assertHasErrorOutput 'String c = c()'

        and:
        executedAndNotSkipped ":b:${language.compileTaskName}"
        executedAndNotSkipped ":c:${language.compileTaskName}"

        where:
        language << getSupportedLanguages()

    }

    @Unroll
    def "change to transitive super-class in different project should trigger recompilation 2"() {
        given:
        beforeEach(language)
        settingsFile << "include 'c', 'd'"

        buildFile << """
            project(':a') {
                dependencies {
                    compile project(':b')
                }
            }
            project(':b') {
                dependencies {
                    compile project(':c')
                }
            }
            project(':c') {
                dependencies {
                    compile project(':d')
                }
            }
        """

        file("a/src/main/${language.name}/A.${language.name}") << "public class A extends B { void a() { b(); String d = d(); } }"
        file("b/src/main/${language.name}/B.${language.name}") << "public class B extends C { void b() { } }"
        file("c/src/main/${language.name}/C.${language.name}") << "public class C extends D { void c() { }; }"
        file("d/src/main/${language.name}/D.${language.name}") << "public class D { String d() { return null; } }"

        when:
        succeeds ":a:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"
        executedAndNotSkipped ":c:${language.compileTaskName}"
        executedAndNotSkipped ":d:${language.compileTaskName}"

        when:
        file("d/src/main/${language.name}/D.${language.name}").text = "public class D { void d() { } }"

        then:
        fails ":a:${language.compileTaskName}"
        failure.assertHasErrorOutput 'String d = d();'

        and:
        executedAndNotSkipped ":b:${language.compileTaskName}"
        executedAndNotSkipped ":c:${language.compileTaskName}"
        executedAndNotSkipped ":d:${language.compileTaskName}"

        where:
        language << getSupportedLanguages()
    }

    @Issue("gradle/gradle#1913")
    @Unroll
    def "detects changes in compile classpath"() {
        given:
        beforeEach(language)
        buildFile << """
            ${jcenterRepository()}

            dependencies {
               if (project.hasProperty('useCommons')) {
                  implementation 'org.apache.commons:commons-lang3:3.5'
               }

               // There MUST be at least 3 dependencies, in that specific order, for the bug to show up.
               // The reason is that `IncrementalTaskInputs` reports wrong information about deletions at the
               // beginning of a list, when the collection is ordered. It has been agreed not to fix it now, but
               // rather change the incremental compiler not to rely on this incorrect information

               implementation 'net.jcip:jcip-annotations:1.0'
               implementation 'org.slf4j:slf4j-api:1.7.10'
            }
        """
        file("src/main/${language.name}/Client.${language.name}") << """import org.apache.commons.lang3.exception.ExceptionUtils;
            public class Client {
                public void doSomething() {
                    ExceptionUtils.rethrow(new RuntimeException("ok"));
                }
            }
        """

        when:
        executer.withArgument('-PuseCommons')
        succeeds ":${language.compileTaskName}"

        then:
        noExceptionThrown()

        when: "Apache Commons is removed from classpath"
        fails ":${language.compileTaskName}"

        then:
        failure.assertHasCause('Compilation failed; see the compiler error output for details.')

        where:
        language << getSupportedLanguages()
    }

    @Unroll
    def "detects changes in compile classpath order"() {
        given:
        beforeEach(language)
        ['a', 'b'].each {
            // Same class is defined in both project `a` and `b` but with a different ABI
            // so one shadows the other depending on the order on classpath
            file("$it/src/main/${language.name}/A.${language.name}") << """
                public class A {
                    public static String m_$it() { return "ok"; }
                }
            """
        }
        buildFile << """
            ${jcenterRepository()}

            dependencies {
               switch (project.getProperty('order') as int) {
                  case 0:
                    implementation 'org.apache.commons:commons-lang3:3.5'
                    implementation project(':a')
                    implementation project(':b')
                    break
                  case 1:
                    implementation 'org.apache.commons:commons-lang3:3.5'
                    implementation project(':b')
                    implementation project(':a')
               }
            }
        """
        file("src/main/${language.name}/Client.${language.name}") << """import org.apache.commons.lang3.exception.ExceptionUtils;
            public class Client {
                public void doSomething() {
                    ExceptionUtils.rethrow(new RuntimeException(A.m_a()));
                }
            }
        """

        when:
        executer.withArgument('-Porder=0')
        succeeds ":${language.compileTaskName}"

        then:
        noExceptionThrown()

        when: "Order is changed"
        executer.withArgument('-Porder=1')
        fails ":${language.compileTaskName}"

        then:
        failure.assertHasCause('Compilation failed; see the compiler error output for details.')

        where:
        language << getSupportedLanguages()
    }
}
