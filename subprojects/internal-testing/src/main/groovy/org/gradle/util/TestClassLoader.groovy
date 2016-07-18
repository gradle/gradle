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

package org.gradle.util
/**
 * A custom ClassLoader implementation. Used in various places to test that we work with things that are not a
 * URLClassLoader.
 */
class TestClassLoader extends ClassLoader {
    private final List<File> classpath

    TestClassLoader(ClassLoader classLoader, List<File> classpath) {
        super(classLoader)
        this.classpath = classpath
    }

    @Override
    protected URL findResource(String name) {
        for (File file : classpath) {
            if (file.directory) {
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
