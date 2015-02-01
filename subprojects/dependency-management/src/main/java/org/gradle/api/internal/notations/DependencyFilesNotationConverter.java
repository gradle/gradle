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

import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.TypeConversionException;

public class DependencyFilesNotationConverter implements NotationConverter<FileCollection, SelfResolvingDependency> {
    private final Instantiator instantiator;

    public DependencyFilesNotationConverter(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("FileCollections").example("files('some.jar', 'someOther.jar')");
    }

    public void convert(FileCollection notation, NotationConvertResult<? super SelfResolvingDependency> result) throws TypeConversionException {
        result.converted(instantiator.newInstance(DefaultSelfResolvingDependency.class, notation));
    }
}
