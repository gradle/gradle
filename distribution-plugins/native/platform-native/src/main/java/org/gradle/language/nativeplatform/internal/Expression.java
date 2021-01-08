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

import javax.annotation.Nullable;
import java.util.List;

/**
 * A preprocessor expression whose value may be resolved to an include file path.
 * All implementations should be immutable.
 */
public interface Expression {
    /**
     * Returns the expression formatted as text in a source file.
     */
    String getAsSourceText();

    IncludeType getType();

    /**
     * When type is {@link IncludeType#QUOTED} or {@link IncludeType#SYSTEM}, then this returns the path, without the delimiters or escape characters.
     * When type is {@link IncludeType#MACRO} or {@link IncludeType#MACRO_FUNCTION}, then this returns the name of the macro.
     * When type is {@link IncludeType#IDENTIFIER} or {@link IncludeType#TOKEN}, then this returns the token.
     * When type anything else, returns null.
     */
    @Nullable
    String getValue();

    /**
     * Returns the actual arguments of this expression when type is {@link IncludeType#MACRO_FUNCTION}.
     * Returns the left and right arguments of the expression when type is {@link IncludeType#TOKEN_CONCATENATION}.
     * Returns the sequence of arguments when type is {@link IncludeType#ARGS_LIST}.
     * Returns the sequence of expressions when type is {@link IncludeType#EXPRESSIONS}.
     */
    List<Expression> getArguments();

    /**
     * Returns this expression as a macro expansion expression.
     * If the type is {@link IncludeType#IDENTIFIER} replace with a {@link IncludeType#MACRO}
     * If the type is {@link IncludeType#TOKEN_CONCATENATION} replace with a {@link IncludeType#EXPAND_TOKEN_CONCATENATION}
     * Otherwise return this.
     */
    Expression asMacroExpansion();
}
