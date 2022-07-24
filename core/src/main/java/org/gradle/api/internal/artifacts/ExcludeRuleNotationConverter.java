/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;

import javax.annotation.Nullable;

public class ExcludeRuleNotationConverter extends MapNotationConverter<ExcludeRule> {

    private static final NotationParser<Object, ExcludeRule> PARSER =
            NotationParserBuilder.toType(ExcludeRule.class).converter(new ExcludeRuleNotationConverter()).toComposite();

    public static NotationParser<Object, ExcludeRule> parser() {
        return PARSER;
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("Maps with 'group' and/or 'module'").example("[group: 'com.google.collections', module: 'google-collections']");
    }

    protected ExcludeRule parseMap(
        @MapKey(ExcludeRule.GROUP_KEY) @Nullable String group,
        @MapKey(ExcludeRule.MODULE_KEY) @Nullable String module
    ) {
        if (group == null && module == null) {
            throw new InvalidUserDataException("Dependency exclude rule requires 'group' and/or 'module' specified. For example: [group: 'com.google.collections']");
        }
        return new DefaultExcludeRule(group, module);
    }
}
