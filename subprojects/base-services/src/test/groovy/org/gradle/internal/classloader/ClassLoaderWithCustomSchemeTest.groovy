/*
 * Copyright 2017 the original author or authors.
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

import spock.lang.Specification

/**
 * Tests that {@link org.gradle.internal.classloader.ClasspathUtil#getClasspath(ClassLoader)}
 * does not throw when given a classpath containing URLs that do not adhere to the 'file' scheme.
 *
 * Test setup works as follows:
 * <ol>
 *     <li>Registers a custom URL scheme in the setupSpec() method
 *     <li>Create an isolated ClassLoader
 *     <li>Create a new URLClassLoader, adding in our new URL with the custom scheme
 *     <li>Call ClasspathUtil#getClasspath and ensure it doesn't assume every URL on the Classpath maps to a File
 * </ol>
 *
 * Notes about failure conditions:
 * <ul>
 *     <li> MalformedURLException will be thrown if there is a problem registering the custom URL stream handler
 *     <li> IllegalArgumentException will be thrown when ClasspathUtil.getClasspath tries to instantiate a File from a non-'file://' URL scheme
 * </ul>
 */
class ClassLoaderWithCustomSchemeTest extends Specification {
    final DefaultClassLoaderFactory factory = new DefaultClassLoaderFactory()

    def setupSpec() {
        URL.setURLStreamHandlerFactory(new ClassLoaderWithCustomSchemeTestHelper.CustomURLStreamHandlerFactory())
    }

    def 'Filters custom URL schemes on the classpath'() {
        given:
        ClassLoader cl = factory.createIsolatedClassLoader(classpath)
        URL[] customUrls = [new URL(ClassLoaderWithCustomSchemeTestHelper.sCUSTOMURI + '://definitely/not/a/file')]
        URLClassLoader urlcl = new URLClassLoader(customUrls, cl)

        when:
        ClasspathUtil.getClasspath(urlcl)

        then:
        notThrown exceptions

        where:
        exceptions << [MalformedURLException, IllegalArgumentException]
    }


    def getClasspath() {
        return [ClasspathUtil.getClasspathForClass(ClassLoaderWithCustomSchemeTestHelper)]
    }
}
