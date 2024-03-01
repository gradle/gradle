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

package org.gradle.integtests.tooling.fixture

import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.test.fixtures.file.TestFile

class ClassLoaderFixture {
    static void copyClassTo(Class<?> cl, TestFile rootDir) {
        def fileName = cl.name.replace('.', '/') + ".class"
        def file = rootDir.file(fileName)
        file.parentFile.createDir()
        file.bytes = ClassLoaderFixture.class.classLoader.getResource(fileName).bytes
    }

    static ClassLoader actionClassLoader(ClassLoader parent, TestFile... cp) {
        def spec = new FilteringClassLoader.Spec()
        spec.allowPackage("org.gradle.tooling")
        def parentCl = new FilteringClassLoader(parent, spec)
        return new URLClassLoader(cp.collect { it.toURI().toURL() } as URL[], parentCl)
    }
}
