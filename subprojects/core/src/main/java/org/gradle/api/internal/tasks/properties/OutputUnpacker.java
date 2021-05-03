/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.MutableBoolean;

public class OutputUnpacker extends PropertyVisitor.Adapter {

    private final String ownerDisplayName;
    private final FileCollectionFactory fileCollectionFactory;
    private final boolean locationOnly;
    private boolean hasDeclaredOutputs;
    private final UnpackedOutputConsumer unpackedOutputConsumer;

    public OutputUnpacker(String ownerDisplayName, FileCollectionFactory fileCollectionFactory, boolean locationOnly, UnpackedOutputConsumer unpackedOutputConsumer) {
        this.ownerDisplayName = ownerDisplayName;
        this.fileCollectionFactory = fileCollectionFactory;
        this.locationOnly = locationOnly;
        this.unpackedOutputConsumer = unpackedOutputConsumer;
    }

    public interface UnpackedOutputConsumer {
        void visitUnpackedOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertySpec spec);
        void visitEmptyOutputFileProperty(String propertyName, boolean optional, PropertyValue value);
    }

    @Override
    public void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
        hasDeclaredOutputs = true;
        MutableBoolean hasSpecs = new MutableBoolean();
        FileParameterUtils.resolveOutputFilePropertySpecs(ownerDisplayName, propertyName, value, filePropertyType, fileCollectionFactory, locationOnly, spec -> {
            hasSpecs.set(true);
            unpackedOutputConsumer.visitUnpackedOutputFileProperty(propertyName, optional, value, spec);
        });
        if (!hasSpecs.get()) {
            unpackedOutputConsumer.visitEmptyOutputFileProperty(propertyName, optional, value);
        }
    }

    public boolean hasDeclaredOutputs() {
        return hasDeclaredOutputs;
    }
}
