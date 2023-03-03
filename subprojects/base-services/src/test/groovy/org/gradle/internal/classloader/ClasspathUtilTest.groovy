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

import org.gradle.internal.classpath.DefaultClassPath
import spock.lang.Issue
import spock.lang.Specification

class ClasspathUtilTest extends Specification {
    private static final URL JAR_URL = new URL('jar:file:home/duke/duke.jar!/')
    private static final URL HTTP_URL = new URL('http://www.foo.com/bar/')
    private static final File FILE = new File('/home/duke/duke.jar')
    private static final URL FILE_URL = FILE.toURI().toURL()
    def factory = new DefaultClassLoaderFactory()

    def "filters non-file URLs from classpath"() {
        given:
        def classLoader = factory.createIsolatedClassLoader("test", DefaultClassPath.of([]))
        def urlClassLoader = new URLClassLoader([JAR_URL, HTTP_URL, FILE_URL] as URL[], classLoader)

        when:
        def classpath = ClasspathUtil.getClasspath(urlClassLoader)

        then:
        !classpath.asURLs.any { it == JAR_URL || it == HTTP_URL }
        classpath.asURLs.any { it == FILE_URL }
    }

    def "getClasspathForResource for jar scheme with resource name"() {
        given:
        def jarUrlWithResourceName = new URL('jar:file:/home/duke/duke.jar!/Test.class')

        when:
        def file = ClasspathUtil.getClasspathForResource(jarUrlWithResourceName, "Test.class")

        then:
        FILE.getPath() == file.getPath()
    }

    @Issue("https://github.com/gradle/gradle/issues/23625")
    def "getClasspathForResource for jar scheme with prefix resource name"() {
        given:
        def jarUrlWithResourceName = new URL('jar:file:/home/duke/duke.jar!/META-INF/versions/9/Test.class')

        when:
        def file = ClasspathUtil.getClasspathForResource(jarUrlWithResourceName, "Test.class")

        then:
        FILE.getPath() == file.getPath()
    }
}
