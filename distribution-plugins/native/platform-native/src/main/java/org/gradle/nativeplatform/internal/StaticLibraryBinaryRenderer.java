/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform.internal;

import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;

import javax.inject.Inject;

public class StaticLibraryBinaryRenderer extends AbstractNativeBinaryRenderer<StaticLibraryBinarySpec> {
    @Inject
    public StaticLibraryBinaryRenderer(ModelSchemaStore schemaStore) {
        super(schemaStore);
    }

    @Override
    public Class<StaticLibraryBinarySpec> getTargetType() {
        return StaticLibraryBinarySpec.class;
    }

    @Override
    protected void renderOutputs(StaticLibraryBinarySpec binary, TextReportBuilder builder) {
        builder.item("static library file", binary.getStaticLibraryFile());
    }
}
