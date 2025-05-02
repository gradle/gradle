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

package org.gradle.plugins.ide.eclipse.model.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.Library;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * Eclipse calls them 'class folders' on java build path -&gt; libraries tab
 */
public class ClassFoldersCreator {
    public List<Library> create(EclipseClasspath classpath) {
        if (classpath.getClassFolders() == null) {
            return ImmutableList.of();
        }

        FileReferenceFactory fileReferenceFactory = classpath.getFileReferenceFactory();
        return classpath.getClassFolders().stream()
            .map(folder -> {
                Library library = new Library(fileReferenceFactory.fromFile(folder));
                library.setExported(true);
                return library;
            }).collect(toImmutableList());
    }
}
