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

import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.OutputNormalizer;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultCacheableOutputFilePropertySpec extends AbstractFilePropertySpec implements CacheableOutputFilePropertySpec {
    private final String propertySuffix;
    private final File outputFile;
    private final TreeType outputType;

    public DefaultCacheableOutputFilePropertySpec(String propertyName, @Nullable String propertySuffix, File outputFile, TreeType outputType) {
        super(propertyName, OutputNormalizer.class, ImmutableFileCollection.of(outputFile));
        this.propertySuffix = propertySuffix;
        this.outputFile = outputFile;
        this.outputType = outputType;
    }

    @Override
    public String getPropertyName() {
        return propertySuffix != null ? super.getPropertyName() + propertySuffix : super.getPropertyName();
    }

    @Override
    @Nullable
    public File getOutputFile() {
        return outputFile;
    }

    @Override
    public TreeType getOutputType() {
        return outputType;
    }
}
