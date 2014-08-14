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

import org.gradle.api.Action;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;

import java.util.Set;

public class LanguageSourceSetContainer extends DefaultDomainObjectSet<LanguageSourceSet> {
    private final NotationParser<Object, Set<LanguageSourceSet>> sourcesNotationParser = SourceSetNotationParser.parser();

    public LanguageSourceSetContainer() {
        super(LanguageSourceSet.class);
    }

    /**
     * Temporarily, we need to have a 'live' connection between a component's 'main' FunctionalSourceSet and the set of LanguageSourceSets for the component.
     * We should be able to do away with this, once sourceSets are part of the model proper.
     */
    public void addMainSources(FunctionalSourceSet mainSources) {
        mainSources.all(new Action<LanguageSourceSet>() {
            public void execute(LanguageSourceSet languageSourceSet) {
                add(languageSourceSet);
            }
        });
    }

    public void source(Object sources) {
        addAll(sourcesNotationParser.parseNotation(sources));
    }
}
