/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.schema;

import org.gradle.internal.execution.model.OutputNormalizer;
import org.gradle.internal.execution.schema.AbstractFilePropertySchema;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.properties.annotations.PropertyMetadata;

public class DefaultFileOutputPropertySchema extends AbstractFilePropertySchema implements FileOutputPropertySchema {

    private final TreeType outputType;

    public DefaultFileOutputPropertySchema(String qualifiedName, PropertyMetadata metadata, Object parent, TreeType outputType) {
        super(qualifiedName, metadata, parent, OutputNormalizer.INSTANCE);
        this.outputType = outputType;
    }

    @Override
    public TreeType getOutputType() {
        return outputType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        DefaultFileOutputPropertySchema that = (DefaultFileOutputPropertySchema) o;

        return outputType == that.outputType;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + outputType.hashCode();
        return result;
    }
}
