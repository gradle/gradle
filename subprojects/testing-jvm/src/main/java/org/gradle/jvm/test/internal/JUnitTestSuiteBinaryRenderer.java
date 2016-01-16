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
package org.gradle.jvm.test.internal;

import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.jvm.test.JUnitTestSuiteBinarySpec;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;

public class JUnitTestSuiteBinaryRenderer extends JvmTestSuiteBinaryRenderer<JUnitTestSuiteBinarySpec> {
    public JUnitTestSuiteBinaryRenderer(ModelSchemaStore schemaStore) {
        super(schemaStore);
    }

    @Override
    public Class<JUnitTestSuiteBinarySpec> getTargetType() {
        return JUnitTestSuiteBinarySpec.class;
    }

    @Override
    protected void renderDetails(JUnitTestSuiteBinarySpec binary, TextReportBuilder builder) {
        builder.item("JUnit version", binary.getjUnitVersion());
        super.renderDetails(binary, builder);
    }
}
