/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.builtinit.projectspecs.internal

import org.gradle.buildinit.projectspecs.InitProjectGenerator
import org.gradle.buildinit.projectspecs.InitProjectSource
import org.gradle.buildinit.projectspecs.InitProjectSpec
import org.gradle.buildinit.projectspecs.internal.InitProjectSpecLoader

/**
 * A sample {@link InitProjectSource} that can be dynamically configured
 * with example {@link InitProjectSpec}s pre-construction, and then
 * service-loaded to test loading functionality in {@link InitProjectSpecLoader}.
 */
final class TestInitProjectSource implements InitProjectSource {
    private static final List<InitProjectSpec> AVAILABLE_SPECS = []
    private final List<InitProjectSpec> specs

    static void addSpecs(InitProjectSpec... specs) {
        AVAILABLE_SPECS.addAll(specs.toList())
    }

    TestInitProjectSource() {
        specs = new ArrayList<>(AVAILABLE_SPECS)
    }

    @Override
    List<InitProjectSpec> getProjectSpecs() {
        return specs
    }

    @Override
    Class<? extends InitProjectGenerator> getProjectGenerator() {
        return TestInitProjectGenerator
    }
}
