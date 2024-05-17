/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.internal.properties.PropertyValue;

import java.util.List;

@NonNullApi
public class OutputFilesCollector implements OutputUnpacker.UnpackedOutputConsumer {
    private final List<OutputFilePropertySpec> specs = Lists.newArrayList();
    private ImmutableSortedSet<OutputFilePropertySpec> fileProperties;

    public ImmutableSortedSet<OutputFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            fileProperties = FileParameterUtils.collectFileProperties("output", specs.iterator());
        }
        return fileProperties;
    }

    @Override
    public void visitUnpackedOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertySpec spec) {
        specs.add(spec);
    }

    @Override
    public void visitEmptyOutputFileProperty(String propertyName, boolean optional, PropertyValue value) {
    }
}
