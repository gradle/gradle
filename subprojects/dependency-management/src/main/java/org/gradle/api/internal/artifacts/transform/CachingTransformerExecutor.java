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

package org.gradle.api.internal.artifacts.transform;

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Describable;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.util.List;

@ThreadSafe
public interface CachingTransformerExecutor {
    /**
     * Returns the result of applying the given transformer to the given file.
     */
    List<File> getResult(File primaryInput, Transformer transformer, Describable subject);

    boolean contains(File absoluteFile, HashCode inputsHash);
}
