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

package org.gradle.api.reporting.components.internal;

import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.jvm.ClassDirectoryBinarySpec;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.nativeplatform.NativeExecutableBinarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.platform.base.BinarySpec;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.reporting.ReportRenderer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TypeAwareBinaryRenderer extends ReportRenderer<BinarySpec, TextReportBuilder> {
    private final Map<Class<?>, ReportRenderer<BinarySpec, TextReportBuilder>> renderers = new HashMap<Class<?>, ReportRenderer<BinarySpec, TextReportBuilder>>();

    public TypeAwareBinaryRenderer() {
        add(NativeExecutableBinarySpec.class, new NativeExecutableBinaryRenderer());
        add(NativeTestSuiteBinarySpec.class, new NativeTestSuiteBinaryRenderer());
        add(SharedLibraryBinarySpec.class, new SharedLibraryBinaryRenderer());
        add(StaticLibraryBinarySpec.class, new StaticLibraryBinaryRenderer());
        add(JarBinarySpec.class, new JarBinaryRenderer());
        add(ClassDirectoryBinarySpec.class, new ClassDirectoryBinaryRenderer());
        add(PlayApplicationBinarySpec.class, new PlayApplicationBinaryRenderer());
        add(BinarySpec.class, new BinaryRenderer());
    }

    <T extends BinarySpec> void add(final Class<T> type, final ReportRenderer<T, TextReportBuilder> renderer) {
        renderers.put(type, new ReportRenderer<BinarySpec, TextReportBuilder>() {
            @Override
            public void render(BinarySpec model, TextReportBuilder output) throws IOException {
                renderer.render(type.cast(model), output);
            }
        });
    }

    @Override
    public void render(BinarySpec model, TextReportBuilder output) throws IOException {
        Class<?> bestType = null;
        ReportRenderer<BinarySpec, TextReportBuilder> renderer = null;
        for (Map.Entry<Class<?>, ReportRenderer<BinarySpec, TextReportBuilder>> entry : renderers.entrySet()) {
            if (!entry.getKey().isInstance(model)) {
                continue;
            }
            if (bestType == null || bestType.isAssignableFrom(entry.getKey())) {
                bestType = entry.getKey();
                renderer = entry.getValue();
            }
        }
        renderer.render(model, output);
    }
}
