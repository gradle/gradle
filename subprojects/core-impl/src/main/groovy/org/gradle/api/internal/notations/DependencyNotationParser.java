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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;

import java.util.Set;

/**
 * by Szczepan Faber, created at: 11/8/11
 */
public class DependencyNotationParser implements TopLevelNotationParser {

    private final Set<NotationParser<? extends Dependency>> notationParsers;

    //TODO SF - add some coverage when finished refactoring, also add integration coverage for unhappy path
    public DependencyNotationParser(Set<NotationParser<? extends Dependency>> notationParsers) {
        this.notationParsers = notationParsers;
    }

    public Dependency parseNotation(Object dependencyNotation) {
        if (dependencyNotation instanceof Dependency) {
            return (Dependency) dependencyNotation;
        }

        Dependency dependency = null;
        for (NotationParser<? extends Dependency> notationParser : notationParsers) {
            try {
                if (notationParser.canParse(dependencyNotation)) {
                    dependency = notationParser.parseNotation(dependencyNotation);
                    break;
                }
            } catch (Exception e) {
                //TODO SF feels like this exception does not belong here
                throw new GradleException(String.format("Could not create a dependency using notation: %s", dependencyNotation), e);
            }
        }

        if (dependency == null) {
            throw new InvalidUserDataException(String.format("The dependency notation: %s is invalid.",
                    dependencyNotation));
        }
        return dependency;
    }
}
