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

package org.gradle.util
/**
 * A custom ClassLoader implementation that uses unsafe URLs that potentially contain whitespaces.
 */
class FaultyTestClassLoader extends TestClassLoader {

    FaultyTestClassLoader(ClassLoader classLoader, List<File> classpath) {
        super(classLoader, classpath)
    }

    @Override
    protected URL findResource(String name) {
        for (File file : classpath) {
            if (file.directory) {
                def classFile = new File(file, name)
                if (classFile.exists()) {
                    return new URL("file:" + classFile.absolutePath)
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
}
