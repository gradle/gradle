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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.tasks.TaskOutputs;
import org.gradle.api.tasks.compile.CompileOptions;

import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

public class CompilerForkUtils {
    public static void doNotCacheIfForkingViaExecutable(final CompileOptions compileOptions, TaskOutputs outputs) {
        outputs.doNotCacheIf(
            "Forking compiler via ForkOptions.executable",
            spec(element -> compileOptions.isFork() && compileOptions.getForkOptions().getExecutable() != null)
        );
    }
}
