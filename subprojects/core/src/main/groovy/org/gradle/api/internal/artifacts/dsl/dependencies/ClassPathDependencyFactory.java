/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.notations.NotationParser;

public class ClassPathDependencyFactory implements IDependencyImplementationFactory, NotationParser<SelfResolvingDependency> {
    private final ClassPathRegistry classPathRegistry;
    private final Instantiator instantiator;
    private final FileResolver fileResolver;

    public ClassPathDependencyFactory(Instantiator instantiator, ClassPathRegistry classPathRegistry,
                                      FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.classPathRegistry = classPathRegistry;
        this.fileResolver = fileResolver;
    }

    public <T extends Dependency> T createDependency(Class<T> type, Object userDependencyDescription)
            throws IllegalDependencyNotation {
        if (!canParse(userDependencyDescription)) {
            throw new IllegalDependencyNotation();
        }

        return type.cast(parseNotation(userDependencyDescription));
    }

    public boolean canParse(Object notation) {
        return notation instanceof DependencyFactory.ClassPathNotation;
    }

    public SelfResolvingDependency parseNotation(Object notation) {
        assert notation instanceof DependencyFactory.ClassPathNotation : "Parser only accepts DependencyFactory.ClassPathNotation instances";

        DependencyFactory.ClassPathNotation classPathNotation = (DependencyFactory.ClassPathNotation) notation;
        FileCollection files = fileResolver.resolveFiles(classPathRegistry.getClassPathFiles(classPathNotation.name()));
        return instantiator.newInstance(DefaultSelfResolvingDependency.class, files);
    }
}
