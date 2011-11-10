/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.notations;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.internal.notations.api.TopLevelNotationParser;

import java.util.Set;

/**
 * by Szczepan Faber, created at: 11/8/11
 */
public class DependencyNotationParser implements TopLevelNotationParser, NotationParser<Dependency> {

    private final NotationParser<Set<Dependency>> delegate;

    public DependencyNotationParser(Set<NotationParser<? extends Dependency>> compositeParsers) {
        delegate = new NotationParserBuilder()
                .resultingType(Dependency.class)
                .parsers((Set) compositeParsers)
                .invalidNotationMessage("The dependency notation cannot be used to form the dependency.\n"
                            + "The most typical dependency notation types/formats:\n"
                            + "  - Strings, e.g. 'org.gradle:gradle-core:1.0'\n"
                            + "  - Maps, e.g. [group: 'org.gradle', name:'gradle-core', version: '1.0']\n"
                            + "  - Projects, e.g. project(':some:project:path')\n"
                            + "  - instances of FileCollection, e.g. files('some.jar', 'someOther.jar')\n"
                            + "  - instances of Dependency type\n"
                            + "  - A Collection of above (nested collections/arrays will be flattened)\n"
                            + "Comprehensive documentation on dependency notations is available in DSL reference for DependencyHandler type.")
                .build();
    }

    DependencyNotationParser(NotationParser<Set<Dependency>> delegate) {
        this.delegate = delegate;
    }

    public Dependency parseNotation(Object dependencyNotation) {
        try {
            Set<Dependency> parsed = delegate.parseNotation(dependencyNotation);
            //TODO SF - until some more refactorings are done in the DependencyHandler, we just get first element from the set:
            return parsed.iterator().next();
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException(String.format("Could not create a dependency using notation: %s", dependencyNotation), e);
        }
    }

    public boolean canParse(Object notation) {
        return delegate.canParse(notation);
    }
}
