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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;

import java.util.function.Consumer;

public class PropertyFileCollection extends CompositeFileCollection {
    private final String ownerDisplayName;
    private final String type;
    private final String propertyName;
    private final FileCollectionInternal files;
    private String displayName;

    public PropertyFileCollection(String ownerDisplayName, String propertyName, String type, FileCollectionInternal files) {
        this.ownerDisplayName = ownerDisplayName;
        this.type = type;
        this.propertyName = propertyName;
        this.files = files;
    }

    @Override
    public String getDisplayName() {
        if (displayName == null) {
            displayName = type + " files for " + ownerDisplayName + " property '" + propertyName + "'";
        }
        return displayName;
    }

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        visitor.accept(files);
    }
}
