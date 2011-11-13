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
package org.gradle.api.internal.notations;

import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule;
import org.gradle.api.internal.notations.api.NotationParser;

import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultClientModuleFactory implements NotationParser<ClientModule> {
    private final NotationParser<Set<ClientModule>> delegate;

    public DefaultClientModuleFactory(Instantiator instantiator) {
        delegate = new NotationParserBuilder()
            .parser(new DependencyStringNotationParser<DefaultClientModule>(instantiator, DefaultClientModule.class))
            .parser(new DependencyMapNotationParser<DefaultClientModule>(instantiator, DefaultClientModule.class))
            .invalidNotationMessage("Client module dependency notation cannot be used to form a client module.")
            .build(); //TODO SF - to multi
    }

    public <T extends Dependency> T createDependency(Class<T> type, Object notation) throws IllegalDependencyNotation {
        if (!canParse(notation)) {
            throw new IllegalDependencyNotation();
        }
        return type.cast(parseNotation(notation));
    }

    public boolean canParse(Object notation) {
        return delegate.canParse(notation);
    }

    public ClientModule parseNotation(Object notation) {
        return delegate.parseNotation(notation).iterator().next();
    }
}
