/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.OutputNormalizer;

import java.io.File;

/**
 * A (possibly filtered) directory tree which is used as an output.
 */
public class DirectoryTreeOutputFilePropertySpec extends AbstractFilePropertySpec implements OutputFilePropertySpec {
    private final File root;

    public DirectoryTreeOutputFilePropertySpec(String propertyName, FileCollectionInternal files, File root) {
        super(propertyName, OutputNormalizer.class, files);
        this.root = root;
    }

    @Override
    public TreeType getOutputType() {
        return TreeType.DIRECTORY;
    }

    @Override
    public File getOutputFile() {
        return root;
    }
}
