/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativebinaries.language.assembler.internal;

import org.gradle.nativebinaries.internal.BinaryToolSpec;

import java.io.File;
import java.util.List;

/**
 * A compile spec that will be used to generate object files for combining into a native binary.
 */
public interface AssembleSpec extends BinaryToolSpec {
    File getObjectFileDir();

    void setObjectFileDir(File objectFileDir);

    List<File> getSourceFiles();

    void source(Iterable<File> sources);
}
