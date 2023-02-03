/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.classloader


import org.gradle.internal.classpath.DefaultClassPath
import spock.lang.Specification

class DefaultClassLoaderFactoryTest extends Specification {
    final DefaultClassLoaderFactory factory = new DefaultClassLoaderFactory()
    ClassLoader original

    def setup() {
        original = Thread.currentThread().contextClassLoader
    }

    def cleanup() {
        Thread.currentThread().contextClassLoader = original
    }

    def "classes from specified URLs are visible in isolated ClassLoader"() {
        when:
        def cl = factory.createIsolatedClassLoader("test", classpath)
        def c = cl.loadClass(DefaultClassLoaderFactoryTestHelper.name)

        then:
        c.name == DefaultClassLoaderFactoryTestHelper.name
        c != DefaultClassLoaderFactoryTestHelper
    }

    def "application classes are not visible in isolated ClassLoader"() {
        when:
        def cl = factory.createIsolatedClassLoader("test", classpath)
        cl.loadClass(Closure.name)

        then:
        thrown(ClassNotFoundException)
    }

    def "can use XML APIs from filtering ClassLoader when application classes include an XML provider"() {
        assert ClassLoader.getSystemResource("META-INF/services/javax.xml.parsers.SAXParserFactory")

        when:
        def cl = new URLClassLoader(classpath.asURLArray, factory.createFilteringClassLoader(getClass().classLoader, new FilteringClassLoader.Spec()))
        def c = cl.loadClass(DefaultClassLoaderFactoryTestHelper.name)

        then:
        c != DefaultClassLoaderFactoryTestHelper

        when:
        Thread.currentThread().contextClassLoader = cl
        c.getConstructor().newInstance().doStuff()

        then:
        notThrown()
    }

    def getClasspath() {
        return DefaultClassPath.of(ClasspathUtil.getClasspathForClass(DefaultClassLoaderFactoryTestHelper))
    }
}

