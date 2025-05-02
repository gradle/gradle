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

import org.gradle.api.internal.tasks.compile.CompilationFailedException
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import spock.lang.Issue

@ToBeFixedForIsolatedProjects(because = "allprojects")
abstract class AbstractJavaGroovyCompileAvoidanceIntegrationSpec extends AbstractIntegrationSpec {
    abstract boolean isUseJar()

    abstract boolean isIncremental()

    abstract CompiledLanguage getLanguage()

    /**
     * Returns the expected error message when a compilation fails.
     * <p>
     * This method should be overridden by subclasses that have a different expectation,
     * based on their integration level with the problems API
     *
     * @return the expected error message
     * @see CompilationFailedException
     */
    abstract String expectedJavaCompilationFailureMessage();

    def setup() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        buildFile << """
            allprojects {
                if (name in []) apply plugin: 'java-library'
                apply plugin: '${language.name}'
                task emptyDirs(type: Sync) {
                    into 'build/empty-dirs'
                    from 'src/empty-dirs'
                    includeEmptyDirs = true
                }
            }

            ${language.projectGroovyDependencies()}
        """

        if (language == CompiledLanguage.GROOVY) {
            buildFile << language.projectGroovyDependencies()
            FeaturePreviewsFixture.enableGroovyCompilationAvoidance(settingsFile)
        }

        if (isUseJar()) {
            useJar()
        } else {
            useClassesDir(language)
        }

        buildFile << """
            allprojects {
                tasks.withType(AbstractCompile) {
                    options.incremental = ${isIncremental()}
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

    def useClassesDir(CompiledLanguage language) {
        buildFile << """import static org.gradle.api.attributes.Usage.*;
            allprojects {
                configurations.apiElements.outgoing.variants {
                    classes {
                        attributes.attribute(USAGE_ATTRIBUTE, objects.named(Usage, org.gradle.api.internal.artifacts.JavaEcosystemSupport.DEPRECATED_JAVA_API_CLASSES))
                        artifact file: ${language.compileTaskName}.destinationDirectory.asFile.get(), builtBy: ${language.compileTaskName}
                        artifact file: emptyDirs.destinationDir, builtBy: emptyDirs
                        artifact file: processResources.destinationDir, builtBy: processResources
                    }
                }
            }
        """
    }

    def "doesn't recompile when private element of implementation class changes"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
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
                private static void someMethod() {}
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
        // add public constructor, should change
        sourceFile.text = """
            public class ToolImpl {
                public ToolImpl() {}
                public ToolImpl(String s) {}
                public static ToolImpl instance;
                public void execute() { String s = toString(); }
            }
        """

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}", ":b:${language.compileTaskName}"
    }

    def "doesn't recompile when comments and whitespace of implementation class changes"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public class ToolImpl {}
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
    }

    def "doesn't recompile when implementation class code changes"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
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
    }

    def "doesn't recompile when initializer, static initializer or constructor is changed"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public class ToolImpl {
                {}
                static {}
                public ToolImpl() {}
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
        // change initializer, static initializer and constructor
        sourceFile.text = """
            public class ToolImpl {
                { "".trim(); }
                static { int i = 123; }
                public ToolImpl() { System.out.println("created!"); }
            }
        """

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"
    }

    def "recompiles when type of implementation class changes"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/org/ToolImpl.${language.name}")
        sourceFile << """
            package org;
            public class ToolImpl { void m() {} }
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
            ${language == CompiledLanguage.GROOVY ? "@groovy.transform.PackageScope" : ""} interface ToolImpl { void m(); }
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
    }

    def "recompiles when constant value of API changes"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
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
    }

    def "recompiles when generic type signatures of implementation class changes"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
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
            public interface ToolImpl<T> { public <S extends T> void m(java.util.List<S> s); }
        """

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // change type parameters on interface
        sourceFile.text = """
            public interface ToolImpl<T extends CharSequence> { public <S extends T> void m(java.util.List<S> s); }
        """

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // change type parameters on method
        sourceFile.text = """
            public interface ToolImpl<T extends CharSequence> { public <S extends Number> void m(java.util.List<S> s); }
        """

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"
    }

    def "doesn't recompile when implementation resource is changed in various ways"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
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
    }

    def "doesn't recompile when empty directories are changed in various ways"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
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
    }

    def "change to transitive super-class in different project should trigger recompilation"() {
        given:
        settingsFile << "include 'c'"
        buildFile.text = buildFile.text.replace(
            "(name in [])",
            "(name in ['b'])")
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }
            project(':b') {
                dependencies {
                    api project(':c')
                }
            }
        """

        file("a/src/main/${language.name}/A.${language.name}") << """
            public class A extends B {
                void a() {
                    b();
                    String c = c();
                }
                @Override String c() {
                    return null;
                }
            }
        """
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
        failure.assertHasErrorOutput '@Override String c()'

        and:
        executedAndNotSkipped ":b:${language.compileTaskName}"
        executedAndNotSkipped ":c:${language.compileTaskName}"
    }

    def "change to transitive super-class in different project should trigger recompilation 2"() {
        given:
        settingsFile << "include 'c', 'd'"
        buildFile.text = buildFile.text.replace(
            "(name in [])",
            "(name in ['b', 'c'])")
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }
            project(':b') {
                dependencies {
                    api project(':c')
                }
            }
            project(':c') {
                dependencies {
                    api project(':d')
                }
            }
        """

        file("a/src/main/${language.name}/A.${language.name}") << """
            public class A extends B {
                void a() {
                    b();
                }
                @Override String d() {
                    return null;
                }
            }
        """
        file("b/src/main/${language.name}/B.${language.name}") << "public class B extends C { void b() {} }"
        file("c/src/main/${language.name}/C.${language.name}") << "public class C extends D { void c() {}; }"
        file("d/src/main/${language.name}/D.${language.name}") << "public class D { String d() { return null; } }"

        when:
        succeeds ":a:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"
        executedAndNotSkipped ":c:${language.compileTaskName}"
        executedAndNotSkipped ":d:${language.compileTaskName}"

        when:
        file("d/src/main/${language.name}/D.${language.name}").text = "public class D { void d() {} }"

        then:
        fails ":a:${language.compileTaskName}"
        failure.assertHasErrorOutput '@Override String d()'

        and:
        executedAndNotSkipped ":b:${language.compileTaskName}"
        executedAndNotSkipped ":c:${language.compileTaskName}"
        executedAndNotSkipped ":d:${language.compileTaskName}"
    }

    @Issue("gradle/gradle#1913")
    def "detects changes in compile classpath"() {
        given:
        buildFile << """
            ${mavenCentralRepository()}

            dependencies {
               if (providers.gradleProperty('useCommons').present) {
                  implementation 'org.apache.commons:commons-lang3:3.5'
               }

               // There MUST be at least 3 dependencies, in that specific order, for the bug to show up.
               // The reason is that removed `IncrementalTaskInputs` reported wrong information about deletions at the
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
        // Depending on the language, we expect either:
        //  - The
        failure.assertHasCause(expectedJavaCompilationFailureMessage())
    }

    def "detects changes in compile classpath order"() {
        given:
        // Same class is defined in both project `a` and `b` but with a different ABI
        // so one shadows the other depending on the order on classpath
        file("a/src/main/${language.name}/Base.${language.name}") << """
                public class Base {
                    public String foo() { return "ok"; }
                }
            """
        file("b/src/main/${language.name}/Base.${language.name}") << """
                public class Base {
                    public void foo() {}
                }
            """
        buildFile << """
            ${mavenCentralRepository()}

            def order = providers.gradleProperty('order').get() as int

            dependencies {
               switch (order) {
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
            public class Client extends Base {
                @Override
                public String foo() {
                    ExceptionUtils.rethrow(new RuntimeException());
                    return null;
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
        failure.assertHasCause(expectedJavaCompilationFailureMessage())
    }

    @Issue("https://github.com/gradle/gradle/issues/20398")
    def "doesn't recompile when private element of implementation class changes and there are malformed classes on the classpath"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }

            project(':a') {
                compileJava {
                    doLast {
                        // Create a poison class file on the classpath
                        destinationDirectory.file('Poison.class').get().asFile.text = "foo"
                    }
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
                private static void someMethod() {}
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
        // add public constructor, should change
        sourceFile.text = """
            public class ToolImpl {
                public ToolImpl() {}
                public ToolImpl(String s) {}
                public static ToolImpl instance;
                public void execute() { String s = toString(); }
            }
        """

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}", ":b:${language.compileTaskName}"
    }
}
