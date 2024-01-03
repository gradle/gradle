/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser

import com.google.common.collect.ImmutableList
import org.gradle.language.nativeplatform.internal.Expression
import org.gradle.language.nativeplatform.internal.Include
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import org.gradle.language.nativeplatform.internal.IncludeType


class TestIncludeParser {
    static IncludeDirectives systemIncludes(Collection<String> names) {
        return DefaultIncludeDirectives.of(ImmutableList.copyOf(names.collect { new IncludeWithSimpleExpression(it, false, IncludeType.SYSTEM) }), ImmutableList.of(), ImmutableList.of())
    }

    static Include parse(String value, boolean isImport) {
        Expression expression = RegexBackedCSourceParser.parseExpression(value);
        return IncludeWithSimpleExpression.create(expression.getValue(), isImport, expression.getType());
    }
}
