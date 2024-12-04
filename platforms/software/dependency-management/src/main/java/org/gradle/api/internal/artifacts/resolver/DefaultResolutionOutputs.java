/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.resolver;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ArtifactCollectionInternal;
import org.gradle.api.internal.artifacts.configurations.DefaultArtifactCollection;
import org.gradle.api.internal.artifacts.configurations.ResolutionBackedFileCollection;
import org.gradle.api.internal.artifacts.configurations.ResolutionResultProvider;
import org.gradle.api.internal.artifacts.configurations.ResolutionResultProviderBackedSelectedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Actions;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.reflect.Instantiator;

import java.util.Collections;

/**
 * Default implementation of {@link ResolutionOutputsInternal}. This class is in charge of
 * converting internal results in the form of {@link ResolverResults} into public facing types like:
 *
 * <ul>
 *     <li>{@link org.gradle.api.file.FileCollection}</li>
 *     <li>{@link org.gradle.api.artifacts.ArtifactCollection}</li>
 *     <li>{@link org.gradle.api.artifacts.ArtifactView}</li>
 *     <li>{@link org.gradle.api.artifacts.result.ResolvedVariantResult}</li>
 *     <li>{@link org.gradle.api.artifacts.result.ResolvedComponentResult}</li>
 * </ul>
 */
public class DefaultResolutionOutputs implements ResolutionOutputsInternal {

    private final ResolutionAccess resolutionAccess;
    private final TaskDependencyFactory taskDependencyFactory;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final AttributesFactory attributesFactory;
    private final Instantiator instantiator;

    public DefaultResolutionOutputs(
        ResolutionAccess resolutionAccess,
        TaskDependencyFactory taskDependencyFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        AttributesFactory attributesFactory,
        Instantiator instantiator
    ) {
        this.resolutionAccess = resolutionAccess;
        this.taskDependencyFactory = taskDependencyFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.attributesFactory = attributesFactory;
        this.instantiator = instantiator;
    }

    @Override
    public ResolutionResultProvider<ResolverResults> getRawResults() {
        return resolutionAccess.getResults();
    }

    @Override
    public Provider<ResolvedVariantResult> getRootVariant() {
        return new DefaultProvider<>(() -> {
            MinimalResolutionResult resolutionResult = getVisitedGraphResults().getResolutionResult();
            return resolutionResult.getRootSource().get().getVariant(resolutionResult.getRootVariantId());
        });
    }

    @Override
    public Provider<ResolvedComponentResult> getRootComponent() {
        return new DefaultProvider<>(() -> getVisitedGraphResults().getResolutionResult().getRootSource().get());
    }

    /**
     * Get the resolved graph, throwing any non-fatal exception that occurred during resolution.
     */
    private VisitedGraphResults getVisitedGraphResults() {
        VisitedGraphResults graph = resolutionAccess.getResults().getValue().getVisitedGraph();
        graph.getResolutionFailure().ifPresent(ex -> {
            resolutionAccess.getHost().rethrowFailuresAndReportProblems("dependencies", Collections.singleton(ex));
        });
        return graph;
    }

    @Override
    public FileCollectionInternal getFiles() {
        return doGetArtifactView(Actions.doNothing()).getFiles();
    }

    @Override
    public ArtifactCollectionInternal getArtifacts() {
        return doGetArtifactView(Actions.doNothing()).getArtifacts();
    }

    @Override
    public ArtifactView artifactView(Action<? super ArtifactView.ViewConfiguration> action) {
        return doGetArtifactView(action);
    }

    private DefaultArtifactView doGetArtifactView(Action<? super ArtifactView.ViewConfiguration> action) {
        // We use the instantiator to generate closure-accepting methods.
        DefaultArtifactViewConfiguration viewConfiguration = instantiator.newInstance(DefaultArtifactViewConfiguration.class, attributesFactory);
        action.execute(viewConfiguration);

        return new DefaultArtifactView(
            viewConfiguration.lenient,
            viewConfiguration.componentFilter,
            viewConfiguration.reselectVariants,
            viewConfiguration.viewAttributes,

            resolutionAccess,
            taskDependencyFactory,
            calculatedValueContainerFactory,
            attributesFactory
        );
    }

    @VisibleForTesting
    public static class DefaultArtifactView implements ArtifactView {

        // View configuration
        private final boolean lenient;
        private final Spec<? super ComponentIdentifier> componentFilter;
        private final boolean reselectVariants;
        private final AttributeContainerInternal viewAttributes;

        // Services
        private final ResolutionAccess resolutionAccess;
        private final TaskDependencyFactory taskDependencyFactory;
        private final CalculatedValueContainerFactory calculatedValueContainerFactory;
        private final AttributesFactory attributesFactory;

        public DefaultArtifactView(
            boolean lenient,
            Spec<? super ComponentIdentifier> componentFilter,
            boolean reselectVariants,
            AttributeContainerInternal viewAttributes,

            ResolutionAccess resolutionAccess,
            TaskDependencyFactory taskDependencyFactory,
            CalculatedValueContainerFactory calculatedValueContainerFactory,
            AttributesFactory attributesFactory
        ) {
            this.lenient = lenient;
            this.componentFilter = componentFilter;
            this.reselectVariants = reselectVariants;
            this.viewAttributes = viewAttributes;

            this.resolutionAccess = resolutionAccess;
            this.taskDependencyFactory = taskDependencyFactory;
            this.calculatedValueContainerFactory = calculatedValueContainerFactory;
            this.attributesFactory = attributesFactory;
        }

        @Override
        public ArtifactCollectionInternal getArtifacts() {
            return new DefaultArtifactCollection(
                getFiles(),
                lenient,
                resolutionAccess.getHost(),
                calculatedValueContainerFactory
            );
        }

        @Override
        public ResolutionBackedFileCollection getFiles() {
            return new ResolutionBackedFileCollection(
                new ResolutionResultProviderBackedSelectedArtifactSet(
                    resolutionAccess.getResults().map(this::selectArtifacts)
                ),
                lenient,
                resolutionAccess.getHost(),
                taskDependencyFactory
            );
        }

        private SelectedArtifactSet selectArtifacts(ResolverResults results) {
            // If the user set the view attributes, we allow variant matching to fail for no matching variants.
            // If we are using the original request attributes, variant matching should not fail.
            // TODO #27773: This is probably not desired behavior. It can be very confusing to request new attributes and
            // then have an ArtifactView silently return no results. We should add a switch specifying whether you
            // want 0 or 1 artifact result, 1 artifact result, or 1+ artifact results for each graph variant, and then
            // deprecate views that select no artifacts without the user specifying that switch.
            boolean allowNoMatchingVariants = !viewAttributes.isEmpty();

            return results.getVisitedArtifacts().select(new ArtifactSelectionSpec(
                getAttributes(),
                componentFilter,
                reselectVariants,
                allowNoMatchingVariants,
                resolutionAccess.getDefaultSortOrder()
            ));
        }

        @Override
        public ImmutableAttributes getAttributes() {
            ImmutableAttributes baseAttributes = resolutionAccess.getAttributes();

            // The user did not specify any attributes. Use the original request attributes.
            if (viewAttributes.isEmpty()) {
                return baseAttributes;
            }

            // When re-selecting, we do not base the view attributes on the original request attributes.
            if (reselectVariants) {
                return viewAttributes.asImmutable();
            }

            // Otherwise, artifact views without re-selection are based on the original request attributes.
            return attributesFactory.concat(baseAttributes, viewAttributes.asImmutable());
        }
    }

    public static class DefaultArtifactViewConfiguration implements ArtifactView.ViewConfiguration {
        private final AttributeContainerInternal viewAttributes;
        private Spec<? super ComponentIdentifier> componentFilter = Specs.satisfyAll();
        private boolean lenient;
        private boolean reselectVariants;

        public DefaultArtifactViewConfiguration(AttributesFactory attributesFactory) {
            this.viewAttributes = attributesFactory.mutable();
        }

        @Override
        public AttributeContainer getAttributes() {
            return viewAttributes;
        }

        @Override
        public ArtifactView.ViewConfiguration attributes(Action<? super AttributeContainer> action) {
            action.execute(viewAttributes);
            return this;
        }

        @Override
        public ArtifactView.ViewConfiguration componentFilter(Spec<? super ComponentIdentifier> componentFilter) {
            if (this.componentFilter != Specs.SATISFIES_ALL) {
                throw new IllegalStateException("The component filter can only be set once before the view was computed");
            }
            this.componentFilter = componentFilter;
            return this;
        }

        @Override
        public boolean isLenient() {
            return lenient;
        }

        @Override
        public void setLenient(boolean lenient) {
            this.lenient = lenient;
        }

        // TODO: Deprecate this in favor of setLenient(Boolean)
        @Override
        public ArtifactView.ViewConfiguration lenient(boolean lenient) {
            this.lenient = lenient;
            return this;
        }

        @Override
        public ArtifactView.ViewConfiguration withVariantReselection() {
            this.reselectVariants = true;
            return this;
        }
    }
}
