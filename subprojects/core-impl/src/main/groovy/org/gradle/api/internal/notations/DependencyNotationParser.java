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

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;

public class DependencyNotationParser {
    public static NotationParser<Object, Dependency> parser(Instantiator instantiator, DefaultProjectDependencyFactory dependencyFactory, ClassPathRegistry classPathRegistry, FileLookup fileLookup) {
        return NotationParserBuilder
                .toType(Dependency.class)
                .fromCharSequence(new DependencyStringNotationParser<DefaultExternalModuleDependency>(instantiator, DefaultExternalModuleDependency.class))
                .parser(new DependencyMapNotationParser<DefaultExternalModuleDependency>(instantiator, DefaultExternalModuleDependency.class))
                .fromType(FileCollection.class, new DependencyFilesNotationParser(instantiator))
                .fromType(Project.class, new DependencyProjectNotationParser(dependencyFactory))
                .fromType(DependencyFactory.ClassPathNotation.class, new DependencyClassPathNotationParser(instantiator, classPathRegistry, fileLookup.getFileResolver()))
                .invalidNotationMessage("Comprehensive documentation on dependency notations is available in DSL reference for DependencyHandler type.")
                .toComposite();
    }
}
