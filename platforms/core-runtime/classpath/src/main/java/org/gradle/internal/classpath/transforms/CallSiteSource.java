/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.classpath.transforms;

import org.jspecify.annotations.Nullable;

/**
 * The source position of the call being visited.
 * <p>
 * The instrumenting method visitor already tracks both, because the instrumentation-time reporting listener
 * takes them. This exposes them to interceptors that want to bake a call site into the rewritten call, so that
 * it does not have to be discovered by walking the stack at runtime.
 */
public interface CallSiteSource {

    /**
     * The source file of the class being instrumented, or null when it was compiled without one.
     */
    @Nullable
    String getSourceFileName();

    /**
     * The line of the call being visited, or a non-positive number when the class carries no line numbers.
     */
    int getLineNumber();
}
