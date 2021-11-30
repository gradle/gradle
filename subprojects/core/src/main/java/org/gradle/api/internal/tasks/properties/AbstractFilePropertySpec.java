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
import org.gradle.api.tasks.FileNormalizer;

public abstract class AbstractFilePropertySpec extends AbstractPropertySpec implements FilePropertySpec {
    private final Class<? extends FileNormalizer> normalizer;
    private final FileCollectionInternal files;

    public AbstractFilePropertySpec(String propertyName, Class<? extends FileNormalizer> normalizer, FileCollectionInternal files) {
        super(propertyName);
        this.normalizer = normalizer;
        this.files = files;
    }

    @Override
    public Class<? extends FileNormalizer> getNormalizer() {
        return normalizer;
    }

    @Override
    public FileCollectionInternal getPropertyFiles() {
        return files;
    }

    @Override
    public String toString() {
        return getPropertyName() + " (" + getNormalizer().getSimpleName().replace("Normalizer", "") + ")";
    }
}
