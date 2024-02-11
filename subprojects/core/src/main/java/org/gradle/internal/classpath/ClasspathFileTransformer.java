/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.internal.classpath.types.InstrumentingTypeRegistry;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;

import java.io.File;

public interface ClasspathFileTransformer {
    File transform(File source, FileSystemLocationSnapshot sourceSnapshot, File cacheDir, InstrumentingTypeRegistry typeRegistry);

    ClasspathFileHasher getFileHasher();
}
