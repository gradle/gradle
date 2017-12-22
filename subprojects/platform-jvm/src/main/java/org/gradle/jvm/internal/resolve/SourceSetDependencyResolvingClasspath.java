/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.jvm.internal.resolve;

import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.jvm.internal.DependencyResolvingClasspath;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;

import java.util.List;

public class SourceSetDependencyResolvingClasspath extends DependencyResolvingClasspath {

    public SourceSetDependencyResolvingClasspath(
        BinarySpecInternal binarySpec,
        LanguageSourceSet sourceSet,
        Iterable<DependencySpec> dependencies,
        ArtifactDependencyResolver dependencyResolver,
        VariantsMetaData binaryVariants,
        List<ResolutionAwareRepository> remoteRepositories,
        AttributesSchemaInternal attributesSchema,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        BuildOperationExecutor buildOperationExecutor) {
        super(binarySpec,
            "source set '" + sourceSet.getDisplayName() + "'",
            dependencyResolver,
            remoteRepositories,
            new JvmLibraryResolveContext(binarySpec.getId(), binaryVariants, dependencies, UsageKind.API, sourceSet.getDisplayName(), moduleIdentifierFactory), attributesSchema, buildOperationExecutor);
    }

}
