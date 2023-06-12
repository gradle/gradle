/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.gradle.api.plugins.jvm.JvmTargetMatrixAxes;
import org.gradle.testing.base.MatrixAxis;

import java.util.Arrays;

public class DefaultJvmTargetMatrixAxes implements JvmTargetMatrixAxes {
    private SetMultimap<Class<? extends MatrixAxis<?>>, Object> axes = HashMultimap.create();

    @Override
    public <T> void axis(Class<? extends MatrixAxis<T>> axis, T... values) {
        axis(axis, Arrays.asList(values));
    }

    @Override
    public <T> void axis(Class<? extends MatrixAxis<T>> axis, Iterable<T> values) {
        if (axes == null) {
            throw new IllegalStateException("Cannot configure axes after they have been used.");
        }
        axes.putAll(axis, values);
    }
}
