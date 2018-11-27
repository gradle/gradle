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

import com.google.common.collect.ImmutableList;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Try;

import java.io.File;

@ThreadSafe
public interface TransformerInvoker {
    /**
     * Returns the result of applying the given transformer to the given file.
     */
    Try<ImmutableList<File>> invoke(Transformer transformer, File primaryInput, ArtifactTransformDependenciesInternal dependencies, TransformationSubject subject);
}
