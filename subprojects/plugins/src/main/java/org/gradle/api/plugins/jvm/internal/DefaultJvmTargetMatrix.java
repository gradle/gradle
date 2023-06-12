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

import org.gradle.api.Action;
import org.gradle.api.plugins.jvm.JvmTargetMatrix;
import org.gradle.api.plugins.jvm.JvmTargetSpec;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.internal.ImmutableActionSet;

import java.util.List;

public class DefaultJvmTargetMatrix implements JvmTargetMatrix {
    private ImmutableActionSet<? super JvmTestSuiteTarget> actions;

    @Override
    public void all(Action<? super JvmTestSuiteTarget> action) {

    }

    @Override
    public void with(Action<? super JvmTargetSpec> spec, Action<? super JvmTestSuiteTarget> action) {

    }
}
