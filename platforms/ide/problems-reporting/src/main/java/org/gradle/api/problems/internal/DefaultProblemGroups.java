/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gradle.api.problems.GradleProblemGroup;
import org.gradle.api.problems.DependenciesProblemGroup;
import org.gradle.api.problems.TransformationProblemGroup;
import org.gradle.api.problems.CompilationProblemGroup;
import org.gradle.api.problems.ExternalProcessesProblemGroup;
import org.gradle.api.problems.PackagingProblemGroup;
import org.gradle.api.problems.VerificationProblemGroup;
import org.gradle.api.problems.DocumentationProblemGroup;
import org.gradle.api.problems.ProvisioningProblemGroup;
import org.gradle.api.problems.DeliveryProblemGroup;
import org.gradle.api.problems.OthersProblemGroup;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemGroups;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * The predefined problem group hierarchy. Stateless apart from the root singletons, so a single instance is shared by all
 * {@code Problems} services in a process; group equality is structural (name and parent), so instance identity does not matter
 * across processes.
 */
final class DefaultProblemGroups implements ProblemGroups {

    public static final DefaultProblemGroups INSTANCE = new DefaultProblemGroups();

    private final PredefinedGradleProblemGroup gradle = new PredefinedGradleProblemGroup();
    private final PredefinedDependenciesProblemGroup dependencies = new PredefinedDependenciesProblemGroup();
    private final PredefinedTransformationProblemGroup transformation = new PredefinedTransformationProblemGroup();
    private final PredefinedCompilationProblemGroup compilation = new PredefinedCompilationProblemGroup();
    private final PredefinedExternalProcessesProblemGroup externalProcesses = new PredefinedExternalProcessesProblemGroup();
    private final PredefinedPackagingProblemGroup packaging = new PredefinedPackagingProblemGroup();
    private final PredefinedVerificationProblemGroup verification = new PredefinedVerificationProblemGroup();
    private final PredefinedDocumentationProblemGroup documentation = new PredefinedDocumentationProblemGroup();
    private final PredefinedProvisioningProblemGroup provisioning = new PredefinedProvisioningProblemGroup();
    private final PredefinedDeliveryProblemGroup delivery = new PredefinedDeliveryProblemGroup();
    private final PredefinedOthersProblemGroup others = new PredefinedOthersProblemGroup();
    private final ImmutableList<ProblemGroup> roots = ImmutableList.<ProblemGroup>of(gradle, dependencies, transformation, compilation, externalProcesses, packaging, verification, documentation, provisioning, delivery, others);
    private final ImmutableMap<String, ResolvableProblemGroup> rootsByName = Maps.uniqueIndex(Arrays.asList(gradle, dependencies, transformation, compilation, externalProcesses, packaging, verification, documentation, provisioning, delivery, others), ResolvableProblemGroup::getName);

    private DefaultProblemGroups() {
    }

    @Override
    public GradleProblemGroup getGradle() {
        return gradle;
    }

    @Override
    public DependenciesProblemGroup getDependencies() {
        return dependencies;
    }

    @Override
    public TransformationProblemGroup getTransformation() {
        return transformation;
    }

    @Override
    public CompilationProblemGroup getCompilation() {
        return compilation;
    }

    @Override
    public ExternalProcessesProblemGroup getExternalProcesses() {
        return externalProcesses;
    }

    @Override
    public PackagingProblemGroup getPackaging() {
        return packaging;
    }

    @Override
    public VerificationProblemGroup getVerification() {
        return verification;
    }

    @Override
    public DocumentationProblemGroup getDocumentation() {
        return documentation;
    }

    @Override
    public ProvisioningProblemGroup getProvisioning() {
        return provisioning;
    }

    @Override
    public DeliveryProblemGroup getDelivery() {
        return delivery;
    }

    @Override
    public OthersProblemGroup getOthers() {
        return others;
    }

    /**
     * All predefined root groups in the order defined by the specification.
     */
    public List<ProblemGroup> getRoots() {
        return roots;
    }

    /**
     * Returns the predefined root group with the given name, or {@code null} if there is none.
     */
    @Nullable
    public ResolvableProblemGroup findRoot(String name) {
        return rootsByName.get(name);
    }

}
