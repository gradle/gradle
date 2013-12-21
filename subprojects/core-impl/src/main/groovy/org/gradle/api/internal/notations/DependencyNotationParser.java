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

package org.gradle.api.internal.notations;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;

import java.util.Collection;

public class DependencyNotationParser implements NotationParser<Object, Dependency> {

    private final NotationParser<Object, Dependency> delegate;

    public DependencyNotationParser(Iterable<NotationParser<Object, ? extends Dependency>> compositeParsers) {
        delegate = new NotationParserBuilder<Dependency>()
                .resultingType(Dependency.class)
                .parsers(compositeParsers)
                .invalidNotationMessage("Comprehensive documentation on dependency notations is available in DSL reference for DependencyHandler type.")
                .toComposite();
    }

    DependencyNotationParser(NotationParser<Object, Dependency> delegate) {
        this.delegate = delegate;
    }

    public void describe(Collection<String> candidateFormats) {
        delegate.describe(candidateFormats);
    }

    public Dependency parseNotation(Object dependencyNotation) {
        try {
            return delegate.parseNotation(dependencyNotation);
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException(String.format("Could not create a dependency using notation: %s", dependencyNotation), e);
        }
    }
}
