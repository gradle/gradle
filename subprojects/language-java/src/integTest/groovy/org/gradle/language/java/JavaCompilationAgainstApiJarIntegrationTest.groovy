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

package org.gradle.language.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.util.Requires
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.language.java.JavaIntegrationTesting.applyJavaPlugin
import static org.gradle.language.java.JavaIntegrationTesting.expectJavaLangPluginDeprecationWarnings
import static org.gradle.util.TestPrecondition.FIX_TO_WORK_ON_JAVA9

class JavaCompilationAgainstApiJarIntegrationTest extends AbstractIntegrationSpec {

    private void mainLibraryDependingOnApi(DependencyScope scope = DependencyScope.SOURCES, boolean declaresApi = true) {
        buildFile << """
model {
    components {
        myLib(JvmLibrarySpec)""" + (declaresApi ? """ {
            api {
                exports 'com.acme'
            }
        }""" : '') + """
        main(JvmLibrarySpec) {
            ${scope.declarationFor 'myLib'}
        }
    }
}
"""
    }

    static Collection<DependencyScope> scopes = DependencyScope.values()

    private void testAppDependingOnApiClass() {
        file('src/main/java/com/acme/TestApp.java') << '''package com.acme;

import com.acme.Person;

public class TestApp {
    private Person person;
}

'''
    }

    void updateFile(String path, String contents) {
        // add a small delay in order to avoid FS synchronization issues
        // the errors often look like this:
        // bad class file: /home/cchampeau/DEV/PROJECTS/GITHUB/gradle/subprojects/language-java/build/tmp/test files/JavaCompilationAgainstApiJarIntegrationTest/consuming_source_is...s_changes/qgrf/build/jars/myLibApiJar/myLib.jar(com/acme/Person.class)
        // unable to access file: corrupted zip file
        // and the reason is unclear now.
        // TODO: investigate this issue
        sleep(1000)
        file(path).write(contents)
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "fails compilation when referencing a non-API class from a #scope dependency"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(scope)

        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {}
'''
        file('src/myLib/java/internal/PersonInternal.java') << '''package internal;
import com.acme.Person;

public class PersonInternal extends Person {}
'''

        and:
        file('src/main/java/com/acme/TestApp.java') << '''package com.acme;

import internal.PersonInternal;

public class TestApp {
    private PersonInternal person;
}
'''

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        fails ':mainJar'
        failure.assertHasDescription("Execution failed for task ':compileMainJarMainJava'.")
        failure.assertHasCause("Compilation failed; see the compiler error output for details.")

        where:
        scope << scopes
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "changing API class in ABI breaking way should trigger recompilation of a consuming library with #scope dependency when an API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(scope, api)

        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {}
'''
        file('src/myLib/java/internal/PersonInternal.java') << '''package internal;
import com.acme.Person;

public class PersonInternal extends Person {}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    public String name;
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled("MyLib")
        recompiled("Main")

        where:
        scope << scopes * 2
        api << ([true]*scopes.size() + [false]*scopes.size())

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "changing non-API class should not trigger recompilation of a consuming library with #scope dependency when API is declared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(scope)

        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {}
'''
        file('src/myLib/java/internal/PersonInternal.java') << '''package internal;
import com.acme.Person;

public class PersonInternal extends Person {}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/internal/PersonInternal.java', '''package internal;
import com.acme.Person;

public class PersonInternal extends Person {
    private String name;
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        notRecompiled 'Main'

        where:
        scope << scopes
    }

    @Unroll
    @Issue('Need to investigate the reason for the Java 9 failure')
    @ToBeFixedForInstantExecution
    def "changing comment in API class should not trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)

        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public String toString() { return name; }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;

    // this is a comment that will introduce a line number change
    // so the .class files are going to be different
    public String toString() { return name; }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled "MyLib"
        notRecompiled "Main"

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @Requires(FIX_TO_WORK_ON_JAVA9)
    @ToBeFixedForInstantExecution
    def "changing method body of API class should not trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)

        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public String toString() { return name; }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;
    public String toString() { return "Name: "+name; }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        notRecompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Ignore("Requires a better definition of what ABI means")
    def "consuming source is not recompiled when overriding a method from a superclass in source dependency"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi()
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;
    public String toString() { return "Name: "+name; }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        notRecompiled 'Main'
    }

    @ToBeFixedForInstantExecution
    def "changing signature of public method of API class should trigger recompilation of the consuming library"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi()
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;
    public void sayHello(String greeting) {
        System.out.println(greeting + ", " + name);
    }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        recompiled 'Main'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "extraction of private method in API class should not trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)

        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public void sayHello() {
        System.out.println(greeting());
    }

    private String greeting() {
        return "Hello, "+name;
    }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;
    public void sayHello() {
        System.out.println("Hello, " + name);
    }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        notRecompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "changing the order of public methods of API class should not trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)

        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public void sayHello() {
        System.out.println(greeting());
    }

    public String greeting() {
        return "Hello, "+name;
    }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;

    public String greeting() {
        return "Hello, "+name;
    }

    public void sayHello() {
        System.out.println(greeting());
    }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        notRecompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "adding a private field to an API class should not trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;
    private int age;

    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        notRecompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "adding a private method to an API class should not trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;

    private int getAge() { return 42; }

    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        notRecompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "changing the order of members of API class should not trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    public String intro;
    public String name;

    public String greeting() {
       return intro +"," + name;
    }

    public void sayHello() {
        System.out.println(greeting());
    }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    public void sayHello() {
        System.out.println(greeting());
    }

    public String greeting() {
       return intro + "," + name;
    }

    public String name;
    public String intro;
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        notRecompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "changing an API field of an API class should trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;
    public int age;
    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;

    public boolean isFamous;

    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        recompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "changing the superclass of an API class should trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person extends Named {
    public void sayHello() {
    }
}
'''
        file('src/myLib/java/com/acme/Named.java') << '''package com.acme;

public class Named {
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {

    public void sayHello() {

    }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        recompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "changing the interfaces of an API class should trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person implements Named {
    public String getName() { return "Foo"; }
    public void setName(String name) {}
    public void sayHello() {
    }
}
'''
        file('src/myLib/java/com/acme/Named.java') << '''package com.acme;

public interface Named {
    public String getName();
    public void setName(String name);
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    public String getName() { return "Foo"; }
    public void setName(String name) {}
    public void sayHello() {
    }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        recompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "changing order of annotations on API class should not trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)
        file('src/myLib/java/com/acme/Ann1.java') << '''package com.acme;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Ann1 {
    String value();
}
'''
        file('src/myLib/java/com/acme/Ann2.java') << '''package com.acme;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Ann2 {
    String a();
    String b();
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

@Ann1("foo")
@Ann2(a="bar", b="baz")
public class Person {
    private String name;
    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

@Ann2(b="baz", a="bar")
@Ann1("foo")
public class Person {
    private String name;
    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        notRecompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "changing order of annotations on API method should not trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)
        file('src/myLib/java/com/acme/Ann1.java') << '''package com.acme;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Ann1 {
    String value();
}
'''
        file('src/myLib/java/com/acme/Ann2.java') << '''package com.acme;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Ann2 {
    String a();
    String b();
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;

    @Ann1("foo")
    @Ann2(a="bar", b="baz")
    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;

    @Ann2(b="baz", a="bar")
    @Ann1("foo")
    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        notRecompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "changing order of annotations on API method parameter should not trigger recompilation of the consuming library when #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)
        file('src/myLib/java/com/acme/Ann1.java') << '''package com.acme;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Ann1 {
    String value();
}
'''
        file('src/myLib/java/com/acme/Ann2.java') << '''package com.acme;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Ann2 {
    String a();
    String b();
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    private String name;

    public void sayHello(
       @Ann1("foo")
       @Ann2(a="bar", b="baz") String intro) {
          System.out.println(intro + ", "+name);
    }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    private String name;

    public void sayHello(
       @Ann2(b="baz", a="bar")
       @Ann1("foo")
       String intro) {
          System.out.println(intro + ", "+name);
    }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        notRecompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "changing order of annotations on API field should not trigger recompilation of the consuming library when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)
        file('src/myLib/java/com/acme/Ann1.java') << '''package com.acme;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Ann1 {
    String value();
}
'''
        file('src/myLib/java/com/acme/Ann2.java') << '''package com.acme;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Ann2 {
    String a();
    String b();
}
'''
        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {
    @Ann1("foo")
    @Ann2(a="bar", b="baz")
    public String name;

    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        when:
        updateFile('src/myLib/java/com/acme/Person.java', '''package com.acme;

public class Person {
    @Ann2(b="baz", a="bar")
    @Ann1("foo")
    public String name;

    public void sayHello() {
        System.out.println("Hello, "+name);
    }
}
''')
        then:
        expectJavaLangPluginDeprecationWarnings(executer)
        succeeds ':mainJar'

        and:
        recompiled 'MyLib'
        notRecompiled 'Main'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "building the API jar should not depend on the runtime jar when API is #apiDeclared"() {
        given:
        applyJavaPlugin(buildFile, executer)
        mainLibraryDependingOnApi(DependencyScope.SOURCES, api)

        file('src/myLib/java/com/acme/Person.java') << '''package com.acme;

public class Person {}
'''
        file('src/myLib/java/internal/PersonInternal.java') << '''package internal;
import com.acme.Person;

public class PersonInternal extends Person {}
'''

        and:
        testAppDependingOnApiClass()

        expect:
        succeeds ':myLibApiJar'

        and:
        recompiled 'MyLib'
        notExecuted ':createMyLibJar'
        notExecuted ':myLibJar'

        where:
        api << [true, false]

        and:
        apiDeclared = api?'declared':'not declared'
    }

    private recompiled(String name) {
        executedAndNotSkipped(":compile${name}Jar${name}Java")
        true
    }

    private notRecompiled(String name) {
        skipped(":compile${name}Jar${name}Java")
        true
    }
}
