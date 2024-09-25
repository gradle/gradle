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

import org.gradle.api.file.Directory
import org.gradle.buildinit.projectspecs.InitProjectConfig
import org.gradle.buildinit.projectspecs.InitProjectGenerator

/**
 * A sample {@link InitProjectGenerator} implementation for testing purposes.
 * <p>
 * Always creates a file named {@link #OUTPUT_FILE} with the given output text.
 */
class TestInitProjectGenerator implements InitProjectGenerator {
    public static final OUTPUT_FILE = "project.output"
    private String output

    TestInitProjectGenerator(String output = "Hello, World!") {
        this.output = output
    }

    @Override
    void generate(InitProjectConfig config, Directory projectDir) {
        projectDir.file(OUTPUT_FILE) << output
    }
}
