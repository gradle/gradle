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
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import javax.tools.ToolProvider

class MixInLegacyTypesClassLoaderTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
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
        def loader = new MixInLegacyTypesClassLoader(groovyClassLoader, new DefaultClassPath(classesDir))

        def cl = loader.loadClass(className)
        cl.classLoader.is(loader)
        cl.protectionDomain.codeSource.location == classesDir.toURI().toURL()
        cl.package.name == "org.gradle.api.plugins"

        def obj = cl.newInstance()
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
        def loader = new MixInLegacyTypesClassLoader(groovyClassLoader, new DefaultClassPath(classesDir))

        def cl = loader.loadClass(className)
        cl.classLoader.is(loader)
        !GroovyObject.isAssignableFrom(cl)
        cl.protectionDomain.codeSource.location == classesDir.toURI().toURL()
        cl.package.name == "org.gradle.api.plugins"
    }

    def "mixes in empty classes for old types that were removed"() {
        expect:
        def loader = new MixInLegacyTypesClassLoader(groovyClassLoader, new DefaultClassPath())

        def cl = loader.loadClass("org.gradle.messaging.actor.ActorFactory")
        cl.classLoader.is(loader)
        cl.protectionDomain.codeSource.location == null
        cl.package.name == "org.gradle.messaging.actor"
    }

    def "fails for unknown class"() {
        def loader = new MixInLegacyTypesClassLoader(groovyClassLoader, new DefaultClassPath())

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
        def cl = new VisitableURLClassLoader(groovyClassLoader, new DefaultClassPath(classesDir))
        cl.loadClass(className)
    }
}
