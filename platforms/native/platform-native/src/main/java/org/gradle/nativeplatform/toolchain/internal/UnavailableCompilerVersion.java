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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.language.base.compile.CompilerVersion;

/**
 * The version of a compiler that is not available.
 * <p>
 * Querying it fails with the reason the compiler is not available, so a task using it as an input fails when it is validated.
 * Resolving the dependencies of such a task does not query the version, so tasks that are skipped, e.g. because they have no source, do not fail.
 */
public class UnavailableCompilerVersion implements CompilerVersion {
    private final UnavailableToolException failure;

    public UnavailableCompilerVersion(UnavailableToolException failure) {
        this.failure = failure;
    }

    @Override
    public String getType() {
        throw failure;
    }

    @Override
    public String getVendor() {
        throw failure;
    }

    @Override
    public String getVersion() {
        throw failure;
    }
}
