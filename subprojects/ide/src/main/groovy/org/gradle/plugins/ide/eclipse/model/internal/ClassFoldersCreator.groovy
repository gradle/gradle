/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.plugins.ide.eclipse.model.Library

/**
 * Eclipse calls them 'class folders' on java build path->libraries tab
 */
class ClassFoldersCreator {

    List<Library> create(EclipseClasspath classpath) {
        List<Library> out = new LinkedList<Library>()
        FileReferenceFactory fileReferenceFactory = classpath.fileReferenceFactory

        classpath.classFolders.each { File folder ->
            Library library = new Library(fileReferenceFactory.fromFile(folder))
            library.exported = true
            out << library
        }

        return out;
    }
}
