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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.internal.Factory;

/**
 * by Szczepan Faber, created at: 11/8/11
 */
public class DependencyNotationParserFactory implements Factory<NotationParser<Dependency>> {

    private final Iterable<NotationParser<? extends Dependency>> compositeParsers;

    public DependencyNotationParserFactory(Iterable<NotationParser<? extends Dependency>> compositeParsers) {
        this.compositeParsers = compositeParsers;
    }

    public NotationParser<Dependency> create() {
        return new NotationParserBuilder<Dependency>()
                .resultingType(Dependency.class)
                .parsers(compositeParsers)
                .invalidNotationMessage("Comprehensive documentation on dependency notations is available in DSL reference for DependencyHandler type.")
                .toComposite();
    }
}
