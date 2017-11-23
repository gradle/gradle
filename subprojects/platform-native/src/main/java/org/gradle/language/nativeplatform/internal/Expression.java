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

package org.gradle.language.nativeplatform.internal;

import java.util.List;

/**
 * A preprocessor expression whose value may be resolved to an include file path.
 */
public interface Expression {
    /**
     * Returns the expression formatted as text in a source file.
     */
    String getAsSourceText();

    IncludeType getType();

    /**
     * When type is {@link IncludeType#QUOTED} or {@link IncludeType#SYSTEM}, then this returns the path, without the delimiters.
     * When type is {@link IncludeType#MACRO} or {@link IncludeType#MACRO_FUNCTION}, then this returns the name of the macro.
     * When type is {@link IncludeType#OTHER} returns null.
     */
    String getValue();

    /**
     * Returns the actual arguments of this expression, when type is {@link IncludeType#MACRO_FUNCTION}. Otherwise, returns an empty list.
     */
    List<Expression> getArguments();
}
