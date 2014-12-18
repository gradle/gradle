/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.internal.typeconversion.*;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class SourceSetNotationParser {
    public static NotationParser<Object, Set<LanguageSourceSet>> parser() {
        return NotationParserBuilder
                .toType(new TypeInfo<Set<LanguageSourceSet>>(Set.class))
                .converter(new FunctionalSourceSetConverter())
                .converter(new SingleLanguageSourceSetConverter())
                .converter(new LanguageSourceSetCollectionConverter())
                .toComposite();
    }

    private static class FunctionalSourceSetConverter extends TypedNotationConverter<FunctionalSourceSet, Set<LanguageSourceSet>> {
        private FunctionalSourceSetConverter() {
            super(FunctionalSourceSet.class);
        }

        @Override
        protected Set<LanguageSourceSet> parseType(FunctionalSourceSet notation) {
            return notation;
        }
    }

    private static class SingleLanguageSourceSetConverter extends TypedNotationConverter<LanguageSourceSet, Set<LanguageSourceSet>> {
        private SingleLanguageSourceSetConverter() {
            super(LanguageSourceSet.class);
        }

        @Override
        protected Set<LanguageSourceSet> parseType(LanguageSourceSet notation) {
            return Collections.singleton(notation);
        }
    }

    private static class LanguageSourceSetCollectionConverter extends TypedNotationConverter<Collection<LanguageSourceSet>, Set<LanguageSourceSet>> {
        private LanguageSourceSetCollectionConverter() {
            super(new TypeInfo<Collection<LanguageSourceSet>>(Collection.class));
        }

        @Override
        protected Set<LanguageSourceSet> parseType(Collection<LanguageSourceSet> notation) {
            return new LinkedHashSet<LanguageSourceSet>(notation);
        }
    }
}
