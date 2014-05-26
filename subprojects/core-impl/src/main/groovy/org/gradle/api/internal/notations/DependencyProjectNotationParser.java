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

import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.TypeConversionException;

import java.util.Collection;

public class DependencyProjectNotationParser implements NotationConverter<Project, ProjectDependency> {

    private final DefaultProjectDependencyFactory factory;

    public DependencyProjectNotationParser(DefaultProjectDependencyFactory factory) {
        this.factory = factory;
    }

    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add("Projects, e.g. project(':some:project:path').");
    }

    public void convert(Project notation, NotationConvertResult<? super ProjectDependency> result) throws TypeConversionException {
        result.converted(factory.create(notation));
    }
}