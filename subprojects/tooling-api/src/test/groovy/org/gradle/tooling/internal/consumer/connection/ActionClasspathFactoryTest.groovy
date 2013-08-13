/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.internal.classloader.MutableURLClassLoader
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.BuildAction
import org.gradle.util.ClasspathUtil
import org.junit.Rule
import spock.lang.Specification

class ActionClasspathFactoryTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def factory = new ActionClasspathFactory()

    def "determines action and tooling API classpath when loaded via a URLClassLoader"() {
        def cl = urlClassLoader(toolingApiClassPath + isolatedClasses(CustomAction, CustomModel))
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = factory.getClassPathForAction(actionClass)
        def loader = new MutableURLClassLoader(ClassLoader.systemClassLoader.parent, classpath)
        def action = loader.loadClass(CustomAction.name).newInstance()
        action.execute(null)
    }

    def "caches classpath for a given action class"() {
        def cl = customClassLoader(toolingApiClassPath + isolatedClasses(CustomAction, CustomModel))
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = factory.getClassPathForAction(actionClass)
        factory.getClassPathForAction(actionClass) == classpath
    }

    def "determines action and tooling API classpath when loaded via custom ClassLoader implementation"() {
        def cl = customClassLoader(toolingApiClassPath + isolatedClasses(CustomAction, CustomModel))
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = factory.getClassPathForAction(actionClass)
        def loader = new MutableURLClassLoader(ClassLoader.systemClassLoader.parent, classpath)
        def action = loader.loadClass(CustomAction.name).newInstance()
        action.execute(null)
    }

    def "determines action and tooling API classpath when loaded via multiple custom ClassLoader implementations"() {
        def toolingCl = customClassLoader(toolingApiClassPath)
        def cl = customClassLoader(toolingCl, isolatedClasses(CustomAction, CustomModel))
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = factory.getClassPathForAction(actionClass)
        def loader = new MutableURLClassLoader(ClassLoader.systemClassLoader.parent, classpath)
        def action = loader.loadClass(CustomAction.name).newInstance()
        action.execute(null)
    }

    private List<File> getToolingApiClassPath() {
        originalClassPath(BuildAction)
    }

    List<File> originalClassPath(Class<?>... classes) {
        return classes.collect { ClasspathUtil.getClasspathForClass(it) }
    }

    List<File> isolatedClasses(Class<?>... classes) {
        return classes.collect {
            def name = it.name.replace('.', '/') + '.class'
            def classPathRoot = tmpDir.file(it.name)
            def classFile = classPathRoot.file(name)
            def resource = it.classLoader.getResource(name)
            classFile.parentFile.mkdirs()
            classFile.bytes = resource.bytes
            classPathRoot
        }
    }

    /**
     * Returns an isolated URLClassLoader with the given classpath
     */
    URLClassLoader urlClassLoader(ClassLoader parent = ClassLoader.systemClassLoader.parent, List<File> classpath) {
        return new URLClassLoader(classpath.collect { it.toURI().toURL() } as URL[], parent)
    }

    /**
     * Returns a custom ClassLoader with the given classpath
     */
    ClassLoader customClassLoader(ClassLoader parent = ClassLoader.systemClassLoader.parent, List<File> classpath) {
        def loader = new ClassLoader(parent) {
            @Override
            protected URL findResource(String name) {
                for (File file : classpath) {
                    if (file.isDirectory()) {
                        def classFile = new File(file, name)
                        if (classFile.exists()) {
                            return classFile.toURI().toURL()
                        }
                    } else if (file.isFile()) {
                        def url = new URL("jar:${file.toURI().toURL()}!/${name}")
                        try {
                            url.openStream().close()
                            return url
                        } catch (FileNotFoundException) {
                            // Ignore
                        }
                    }
                }
                return null
            }

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                String resource = name.replace('.', '/') + '.class'
                URL url = findResource(resource)
                if (url == null) {
                    throw new ClassNotFoundException("Could not find class '${name}'")
                }
                def byteCode = url.bytes
                return defineClass(name, byteCode, 0, byteCode.length)
            }
        }
        return loader
    }
}
