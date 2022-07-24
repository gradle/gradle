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

package org.gradle.plugins.ide.eclipse.model;

import groovy.util.Node;
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory;

/**
 * A classpath entry representing a library.
 */
public class Library extends AbstractLibrary {
    public Library(Node node, FileReferenceFactory fileReferenceFactory) {
        super(node, fileReferenceFactory);
        setSourcePath(fileReferenceFactory.fromPath((String) node.attribute("sourcepath")));
    }

    public Library(FileReference library) {
        super(library);
    }

    @Override
    public String getKind() {
        return "lib";
    }

    @Override
    public String toString() {
        return "Library" + super.toString();
    }
}
