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

package org.gradle.nativeplatform.test.internal;

import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.nativeplatform.internal.AbstractNativeBinaryRenderer;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;

import javax.inject.Inject;

public class NativeTestSuiteBinaryRenderer extends AbstractNativeBinaryRenderer<NativeTestSuiteBinarySpec> {
    @Inject
    public NativeTestSuiteBinaryRenderer(ModelSchemaStore schemaStore) {
        super(schemaStore);
    }

    @Override
    public Class<NativeTestSuiteBinarySpec> getTargetType() {
        return NativeTestSuiteBinarySpec.class;
    }

    @Override
    protected void renderTasks(NativeTestSuiteBinarySpec binary, TextReportBuilder builder) {
        builder.item("install using task", binary.getTasks().getInstall().getPath());
        builder.item("run using task", binary.getTasks().getRun().getPath());
    }

    @Override
    protected void renderOutputs(NativeTestSuiteBinarySpec binary, TextReportBuilder builder) {
        builder.item("executable file", binary.getExecutableFile());
    }
}
