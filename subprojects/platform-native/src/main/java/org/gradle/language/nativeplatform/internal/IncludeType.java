/*
 * Copyright 2015 the original author or authors.
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

public enum IncludeType {
    /**
     * A system include path, eg {@code <hello.h>}
     */
    SYSTEM,
    /**
     * A quoted include path eg {@code "hello.h"}
     */
    QUOTED,
    /**
     * An identifier that should be macro expanded. These appear in the body of a directive eg {@code #define HEADER ABC}.
     */
    MACRO,
    /**
     * A macro function invocation that should be expanded. These appear in the body of a directive and as arguments to another function eg {@code #define HEADER ABC(X, Y)}.
     */
    MACRO_FUNCTION,
    /**
     * An identifier that should not be macro expanded. These appear as the arguments to a {@link #MACRO_FUNCTION} or {@link #TOKEN_CONCATENATION} eg {@code #include ABC(X, Y)}
     */
    IDENTIFIER,
    /**
     * A macro function argument list. These appear in the body of a directive or as the arguments to a {@link #MACRO_FUNCTION} eg {@code #include ABC(a b c)}.
     */
    ARGS_LIST,
    /**
     * A sequence of expressions. These appear as the arguments to a {@link #MACRO_FUNCTION} eg {@code #include ABC(a b c)}.
     */
    EXPRESSIONS,
    /**
     * A token concatenation expression whose value should not be macro expanded. These appear in the body of macro directives or as the arguments to a {@link #MACRO_FUNCTION} eg {@code #define ABC(X, Y) X ## Y}.
     */
    TOKEN_CONCATENATION,
    /**
     * A token concatenation expression whose value should be macro expanded.
     */
    EXPAND_TOKEN_CONCATENATION,
    /**
     * A single character that is not an identifier. These appear as the arguments to a {@link #MACRO_FUNCTION} eg {@code #include ABC((a b, c))}.
     */
    TOKEN,
    /**
     * All other expressions. These things cannot be resolved to an include path.
     */
    OTHER
}
