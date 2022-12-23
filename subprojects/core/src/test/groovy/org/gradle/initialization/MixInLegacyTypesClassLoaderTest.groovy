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

package org.gradle.initialization

import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.reflect.JavaReflectionUtil
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import javax.tools.ToolProvider

class MixInLegacyTypesClassLoaderTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def classesDir = tmpDir.file("classes")
    def srcDir = tmpDir.file("source")

    def "mixes GroovyObject into JavaPluginConvention"() {
        given:
        def className = "org.gradle.api.plugins.JavaPluginConvention"

        def original = compileJavaToDir(className, """
            package org.gradle.api.plugins;
            class JavaPluginConvention {
                String _prop;
                String getProp() { return _prop; }
                void setProp(String value) { _prop = value; }
                String doSomething(String arg) { return arg; }
            }
        """)
        !GroovyObject.isAssignableFrom(original)

        expect:
        def loader = new MixInLegacyTypesClassLoader(groovyClassLoader, DefaultClassPath.of(classesDir), new DefaultLegacyTypesSupport())

        def cl = loader.loadClass(className)
        cl.classLoader.is(loader)
        cl.protectionDomain.codeSource.location == classesDir.toURI().toURL()
        cl.package.name == "org.gradle.api.plugins"

        def obj = JavaReflectionUtil.newInstance(cl)
        obj instanceof GroovyObject
        obj.getMetaClass()
        obj.metaClass
        obj.setProperty("prop", "value")
        obj.getProperty("prop") == "value"
        obj.invokeMethod("doSomething", "arg") == "arg"

        def newMetaClass = new MetaClassImpl(cl)
        newMetaClass.initialize()
        obj.setMetaClass(newMetaClass) == null
    }

    def "add getters for String constants"() {
        given:
        def className = "org.gradle.api.plugins.JavaPluginConvention"

        def original = compileJavaToDir(className, """
            package org.gradle.api.plugins;
            class JavaPluginConvention {
                public static final String SOME_CONST = "Value";
                public static final String SOME_CONST_WITH_GETTER = "Other Value";
                public static final String SOME_CONST_WITH_RHS_EXPRESSION = doSomething("Derived Value");
                public static final String SOME_CONST_WITH_RHS_EXPRESSION_AND_GETTER = doSomething("Other Derived Value");

                public static String getSomeStuff() { return SOME_CONST; }
                public static String getSOME_CONST_WITH_GETTER() { return SOME_CONST_WITH_GETTER; }
                public static String getSOME_CONST_WITH_RHS_EXPRESSION_AND_GETTER() { return SOME_CONST_WITH_RHS_EXPRESSION_AND_GETTER; }
                String _prop;
                String getProp() { return _prop; }
                void setProp(String value) { _prop = value; }

                private static String doSomething(String arg) { return arg; }
            }
        """)

        when:
        original.getMethod("getSOME_CONST")

        then:
        thrown java.lang.NoSuchMethodException

        when:
        original.getMethod("getSOME_CONST_WITH_RHS_EXPRESSION")

        then:
        thrown java.lang.NoSuchMethodException

        expect:
        def loader = new MixInLegacyTypesClassLoader(groovyClassLoader, DefaultClassPath.of(classesDir), new DefaultLegacyTypesSupport())

        def cl = loader.loadClass(className)
        cl.classLoader.is(loader)

        cl.SOME_CONST == "Value"
        cl.getSOME_CONST() == "Value"

        cl.SOME_CONST_WITH_GETTER == "Other Value"
        cl.getSOME_CONST_WITH_GETTER() == "Other Value"

        cl.SOME_CONST_WITH_RHS_EXPRESSION == "Derived Value"
        cl.getSOME_CONST_WITH_RHS_EXPRESSION() == "Derived Value"

        cl.SOME_CONST_WITH_RHS_EXPRESSION_AND_GETTER == "Other Derived Value"
        cl.getSOME_CONST_WITH_RHS_EXPRESSION_AND_GETTER() == "Other Derived Value"
    }

    def "add getters for booleans"() {
        given:
        def className = "org.gradle.api.plugins.JavaPluginConvention"

        def original = compileJavaToDir(className, """
            package org.gradle.api.plugins;
            class JavaPluginConvention {
                private boolean someBoolean = true;
                private boolean booleanWithGetter = false;
                private static boolean staticBoolean = true;

                public boolean publicBoolean = true;

                public boolean isSomeBoolean() { return someBoolean; }
                public boolean isBooleanWithGetter() { return booleanWithGetter; }
                public boolean getBooleanWithGetter() { return booleanWithGetter; }
                public boolean isWithoutField() { return true; }

                public static boolean isStaticBoolean() { return true; }
            }
        """)

        when:
        original.getMethod("getSomeBoolean")

        then:
        thrown java.lang.NoSuchMethodException

        expect:
        def loader = new MixInLegacyTypesClassLoader(groovyClassLoader, DefaultClassPath.of(classesDir), new DefaultLegacyTypesSupport())

        def cl = loader.loadClass(className)
        def obj = JavaReflectionUtil.newInstance(cl)
        obj.getSomeBoolean() == true
        obj.someBoolean == true

        obj.getBooleanWithGetter() == false
        obj.isBooleanWithGetter() == false
        obj.booleanWithGetter == false

        obj.publicBoolean == true

        when:
        cl.getMethod("getWithoutField")

        then:
        thrown java.lang.NoSuchMethodException

        when:
        cl.getMethod("getPublicBoolean")

        then:
        thrown java.lang.NoSuchMethodException

        when:
        cl.getMethod("getStaticBoolean")

        then:
        thrown java.lang.NoSuchMethodException
    }

    def "does not mix GroovyObject into other types"() {
        given:
        def className = "org.gradle.api.plugins.Thing"

        def original = compileJavaToDir(className, """
            package org.gradle.api.plugins;
            class Thing {
            }
        """)
        !GroovyObject.isAssignableFrom(original)

        expect:
        def loader = new MixInLegacyTypesClassLoader(groovyClassLoader, DefaultClassPath.of(classesDir), new DefaultLegacyTypesSupport())

        def cl = loader.loadClass(className)
        cl.classLoader.is(loader)
        !GroovyObject.isAssignableFrom(cl)
        cl.protectionDomain.codeSource.location == classesDir.toURI().toURL()
        cl.package.name == "org.gradle.api.plugins"
    }

    def "mixes in empty interfaces for old types that were removed"() {
        expect:
        def loader = new MixInLegacyTypesClassLoader(groovyClassLoader, ClassPath.EMPTY, new DefaultLegacyTypesSupport())

        def cl = loader.loadClass("org.gradle.messaging.actor.ActorFactory")
        cl.classLoader.is(loader)
        cl.interface
        cl.protectionDomain.codeSource.location == null
        cl.package.name == "org.gradle.messaging.actor"
    }

    def "fails for unknown class"() {
        def loader = new MixInLegacyTypesClassLoader(groovyClassLoader, ClassPath.EMPTY, new DefaultLegacyTypesSupport())

        when:
        loader.loadClass("org.gradle.Unknown")

        then:
        ClassNotFoundException e = thrown()
    }

    ClassLoader getGroovyClassLoader() {
        def spec = new FilteringClassLoader.Spec()
        spec.allowPackage("groovy")
        return new FilteringClassLoader(getClass().classLoader, spec)
    }

    def compileJavaToDir(String className, String text) {
        srcDir.createDir()
        def srcFile = srcDir.file(className + ".java")
        srcFile.text = text

        def compiler = ToolProvider.systemJavaCompiler
        classesDir.createDir()

        def fileManager = compiler.getStandardFileManager(null, null, null)
        def task = compiler.getTask(null, fileManager, null, ["-d", classesDir.path], null, fileManager.getJavaFileObjects(srcFile))
        task.call()
        def cl = VisitableURLClassLoader.fromClassPath("groovy-loader", groovyClassLoader, DefaultClassPath.of(classesDir))
        cl.loadClass(className)
    }
}
