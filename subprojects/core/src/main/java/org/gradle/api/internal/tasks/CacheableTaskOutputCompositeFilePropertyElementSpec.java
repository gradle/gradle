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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.OutputNormalizer;

import java.io.File;

class CacheableTaskOutputCompositeFilePropertyElementSpec implements CacheableTaskOutputFilePropertySpec {
    private final String propertySuffix;
    private final FileCollection files;
    private final File file;
    private final TreeType outputType;
    private final String parentPropertyName;

    public CacheableTaskOutputCompositeFilePropertyElementSpec(String parentPropertyName, String propertySuffix, File file, TreeType outputType) {
        this.parentPropertyName = parentPropertyName;
        this.outputType = outputType;
        this.propertySuffix = propertySuffix;
        this.files = ImmutableFileCollection.of(file);
        this.file = file;
    }

    @Override
    public String getPropertyName() {
        return parentPropertyName + propertySuffix;
    }

    @Override
    public FileCollection getPropertyFiles() {
        return files;
    }

    @Override
    public File getOutputFile() {
        return file;
    }

    @Override
    public TreeType getOutputType() {
        return outputType;
    }

    @Override
    public Class<? extends FileNormalizer> getNormalizer() {
        return OutputNormalizer.class;
    }

    @Override
    public boolean isOptional() {
        return false;
    }

    @Override
    public void prepareValue() {
        // Ignore, should not be called
    }

    @Override
    public void cleanupValue() {
        // Ignore, should not be called
    }

    @Override
    public int compareTo(TaskPropertySpec o) {
        return getPropertyName().compareTo(o.getPropertyName());
    }

    @Override
    public String toString() {
        return getPropertyName();
    }
}
