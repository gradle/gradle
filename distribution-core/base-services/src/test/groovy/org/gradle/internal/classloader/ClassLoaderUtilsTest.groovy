/*
 * Copyright 2018 the original author or authors.
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

import java.nio.file.Files

class ClassLoaderUtilsTest extends Specification {
    def 'can inject classes to a classloader'() {
        given:
        Class testClass = DefaultClassLoaderFactoryTestHelper
        File classpath = ClasspathUtil.getClasspathForClass(testClass)
        File classFile = new File(classpath, testClass.name.replace('.', '/') + '.class')
        byte[] bytes = Files.readAllBytes(classFile.toPath())
        MyClassLoader myClassLoader = new MyClassLoader()

        when:
        Class klass = ClassLoaderUtils.define(myClassLoader, DefaultClassLoaderFactoryTestHelper.name, bytes)

        then:
        !testClass.classLoader.is(myClassLoader)
        klass.classLoader.is(myClassLoader)
    }
}

class MyClassLoader extends ClassLoader {}
