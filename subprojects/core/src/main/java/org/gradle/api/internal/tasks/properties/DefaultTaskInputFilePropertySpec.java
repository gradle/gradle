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

import org.gradle.api.internal.tasks.TaskInputFilePropertySpec;
import org.gradle.api.internal.tasks.ValidatingValue;
import org.gradle.api.internal.tasks.ValidationAction;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.file.PathToFileResolver;

public class DefaultTaskInputFilePropertySpec extends AbstractInputFilePropertySpec implements TaskInputFilePropertySpec {
    private final String propertyName;
    private final boolean skipWhenEmpty;
    private final boolean optional;
    private final Class<? extends FileNormalizer> normalizer;

    public DefaultTaskInputFilePropertySpec(
        String ownerDisplayName,
        String propertyName,
        PathToFileResolver resolver,
        ValidatingValue value,
        ValidationAction validationAction,
        boolean skipWhenEmpty,
        boolean optional,
        Class<? extends FileNormalizer> normalizer
    ) {
        super(ownerDisplayName, resolver, value, validationAction);
        this.propertyName = propertyName;
        this.skipWhenEmpty = skipWhenEmpty;
        this.optional = optional;
        this.normalizer = normalizer;
    }

    @Override
    public boolean isSkipWhenEmpty() {
        return skipWhenEmpty;
    }

    @Override
    public Class<? extends FileNormalizer> getNormalizer() {
        return normalizer;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }
}
