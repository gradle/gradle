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

package org.gradle.nativebinaries.internal;

import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeInfo;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.TypedNotationParser;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class SourceSetNotationParser {
    public static NotationParser<Object, Set<LanguageSourceSet>> parser() {
        return new NotationParserBuilder<Set<LanguageSourceSet>>()
                .resultingType(new TypeInfo<Set<LanguageSourceSet>>(Set.class))
                .parser(new FunctionalSourceSetConverter())
                .parser(new SingleLanguageSourceSetConverter())
                .parser(new LanguageSourceSetCollectionConverter())
                .toComposite();
    }

    private static class FunctionalSourceSetConverter extends TypedNotationParser<FunctionalSourceSet, Set<LanguageSourceSet>> {
        private FunctionalSourceSetConverter() {
            super(FunctionalSourceSet.class);
        }

        @Override
        protected Set<LanguageSourceSet> parseType(FunctionalSourceSet notation) {
            return notation;
        }
    }

    private static class SingleLanguageSourceSetConverter extends TypedNotationParser<LanguageSourceSet, Set<LanguageSourceSet>> {
        private SingleLanguageSourceSetConverter() {
            super(LanguageSourceSet.class);
        }

        @Override
        protected Set<LanguageSourceSet> parseType(LanguageSourceSet notation) {
            return Collections.singleton(notation);
        }
    }

    private static class LanguageSourceSetCollectionConverter extends TypedNotationParser<Collection<LanguageSourceSet>, Set<LanguageSourceSet>> {
        private LanguageSourceSetCollectionConverter() {
            super(new TypeInfo<Collection<LanguageSourceSet>>(Collection.class));
        }

        @Override
        protected Set<LanguageSourceSet> parseType(Collection<LanguageSourceSet> notation) {
            return new LinkedHashSet<LanguageSourceSet>(notation);
        }
    }
}
