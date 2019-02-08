/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformAction;
import org.gradle.api.internal.artifacts.ArtifactTransformRegistration;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.tasks.properties.InspectionSchemeFactory;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.snapshot.ValueSnapshotter;

import javax.annotation.Nullable;

public class DefaultTransformationRegistrationFactory implements TransformationRegistrationFactory {

    private final IsolatableFactory isolatableFactory;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final InstantiatorFactory instantiatorFactory;
    private final TransformerInvoker transformerInvoker;
    private final ValueSnapshotter valueSnapshotter;
    private final PropertyWalker propertyWalker;
    private final DomainObjectProjectStateHandler domainObjectProjectStateHandler;

    public DefaultTransformationRegistrationFactory(
        IsolatableFactory isolatableFactory,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        InstantiatorFactory instantiatorFactory,
        TransformerInvoker transformerInvoker,
        ValueSnapshotter valueSnapshotter,
        InspectionSchemeFactory inspectionSchemeFactory, DomainObjectProjectStateHandler domainObjectProjectStateHandler
    ) {
        this.isolatableFactory = isolatableFactory;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.instantiatorFactory = instantiatorFactory;
        this.transformerInvoker = transformerInvoker;
        this.valueSnapshotter = valueSnapshotter;
        this.propertyWalker = inspectionSchemeFactory.inspectionScheme(ImmutableSet.of(Input.class, InputFile.class, InputFiles.class, InputDirectory.class, Classpath.class, CompileClasspath.class, Nested.class)).getPropertyWalker();
        this.domainObjectProjectStateHandler = domainObjectProjectStateHandler;
    }

    @Override
    public ArtifactTransformRegistration create(ImmutableAttributes from, ImmutableAttributes to, Class<? extends ArtifactTransformAction> implementation, @Nullable Object parameterObject) {
        Transformer transformer = new DefaultTransformer(
            implementation,
            parameterObject,
            instantiatorFactory,
            from,
            classLoaderHierarchyHasher,
            isolatableFactory,
            valueSnapshotter,
            propertyWalker,
            domainObjectProjectStateHandler);

        return new DefaultArtifactTransformRegistration(from, to, new TransformationStep(transformer, transformerInvoker));
    }

    @Override
    public ArtifactTransformRegistration create(ImmutableAttributes from, ImmutableAttributes to, Class<? extends ArtifactTransform> implementation, Object[] params) {
        Transformer transformer = new LegacyTransformer(implementation, params, instantiatorFactory, from, classLoaderHierarchyHasher, isolatableFactory);
        return new DefaultArtifactTransformRegistration(from, to, new TransformationStep(transformer, transformerInvoker));
    }

    private static class DefaultArtifactTransformRegistration implements ArtifactTransformRegistration {
        private final ImmutableAttributes from;
        private final ImmutableAttributes to;
        private final TransformationStep transformationStep;

        public DefaultArtifactTransformRegistration(ImmutableAttributes from, ImmutableAttributes to, TransformationStep transformationStep) {
            this.from = from;
            this.to = to;
            this.transformationStep = transformationStep;
        }

        @Override
        public AttributeContainerInternal getFrom() {
            return from;
        }

        @Override
        public AttributeContainerInternal getTo() {
            return to;
        }

        @Override
        public TransformationStep getTransformationStep() {
            return transformationStep;
        }
    }
}
