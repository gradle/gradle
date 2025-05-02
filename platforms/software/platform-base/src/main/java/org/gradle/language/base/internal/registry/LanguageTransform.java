/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.base.internal.registry;

import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.TransformationFileType;

import java.util.Map;

/**
 * A registered language transformation.
 */
public interface LanguageTransform<U extends LanguageSourceSet, V extends TransformationFileType> {
    /**
     * The language name.
     */
    String getLanguageName();

    /**
     * The interface type of the language source set.
     */
    Class<U> getSourceSetType();

    /**
     * The output type generated from these language sources.
     */
    Class<V> getOutputType();

    /**
     * The tool extensions that should be added to any binary with these language sources.
     */
    Map<String, Class<?>> getBinaryTools();

    /**
     * The task used to transform sources into code for the target runtime.
     */
    SourceTransformTaskConfig getTransformTask();

    boolean applyToBinary(BinarySpec binary);
}
