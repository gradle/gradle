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

package org.gradle.language.base.internal;


import org.gradle.platform.base.TransformationFileType;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.platform.base.BinarySpec;

import java.util.Map;

/**
 * A registered language.
 */
public interface LanguageRegistration<U extends LanguageSourceSet> {
    /**
     * The name.
     */
    String getName();

    /**
     * The interface type of the language source set.
     */
    Class<U> getSourceSetType();

    /**
     * The implementation type of the language source set.
     */
    Class<? extends U> getSourceSetImplementation();

    /**
     * The tool extensions that should be added to any binary with these language sources.
     */
    Map<String, Class<?>> getBinaryTools();

    /**
     * The output type generated from these language sources.
     */
    Class<? extends TransformationFileType> getOutputType();

    /**
     * The task used to transform sources into code for the target runtime.
     */
    SourceTransformTaskConfig getTransformTask();

    // TODO:DAZ This should be declarative, not imperative
    boolean applyToBinary(BinarySpec binary);

}
