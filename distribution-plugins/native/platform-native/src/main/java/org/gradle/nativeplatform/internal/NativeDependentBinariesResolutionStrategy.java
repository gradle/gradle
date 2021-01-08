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

package org.gradle.nativeplatform.internal;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.api.reporting.dependents.internal.DependentComponentsUtils;
import org.gradle.internal.graph.DirectedGraph;
import org.gradle.internal.graph.DirectedGraphRenderer;
import org.gradle.internal.graph.GraphNodeRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.platform.base.VariantComponentSpec;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.dependents.AbstractDependentBinariesResolutionStrategy;
import org.gradle.platform.base.internal.dependents.DefaultDependentBinariesResolvedResult;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolvedResult;

import javax.annotation.Nullable;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

public class NativeDependentBinariesResolutionStrategy extends AbstractDependentBinariesResolutionStrategy {

    public interface TestSupport {
        boolean isTestSuite(BinarySpecInternal target);

        List<NativeBinarySpecInternal> getTestDependencies(NativeBinarySpecInternal nativeBinary);
    }

    private static class State {
        private final Map<NativeBinarySpecInternal, Set<NativeBinarySpecInternal>> dependencies = Maps.newLinkedHashMap();
        private final Map<NativeBinarySpecInternal, List<NativeBinarySpecInternal>> dependents = Maps.newHashMap();

        void registerBinary(NativeBinarySpecInternal binary) {
            if (dependencies.get(binary) == null) {
                dependencies.put(binary, Sets.<NativeBinarySpecInternal>newLinkedHashSet());
            }
        }

        List<NativeBinarySpecInternal> getDependents(NativeBinarySpecInternal target) {
            List<NativeBinarySpecInternal> result = dependents.get(target);
            if (result == null) {
                result = Lists.newArrayList();
                for (NativeBinarySpecInternal dependentBinary : dependencies.keySet()) {
                    if (dependencies.get(dependentBinary).contains(target)) {
                        result.add(dependentBinary);
                    }
                }
                dependents.put(target, result);
            }
            return result;
        }
    }

    public static final String NAME = "native";

    private final ProjectRegistry<ProjectInternal> projectRegistry;
    private final ProjectModelResolver projectModelResolver;
    private final Cache<String, State> stateCache = CacheBuilder.<String, State>newBuilder()
        .maximumSize(1)
        .expireAfterAccess(10, TimeUnit.SECONDS)
        .build();
    private final Cache<NativeBinarySpecInternal, List<DependentBinariesResolvedResult>> resultsCache = CacheBuilder.<NativeBinarySpecInternal, List<DependentBinariesResolvedResult>>newBuilder()
        .maximumSize(3000)
        .expireAfterAccess(10, TimeUnit.SECONDS)
        .build();

    private TestSupport testSupport;

    public NativeDependentBinariesResolutionStrategy(ProjectRegistry<ProjectInternal> projectRegistry, ProjectModelResolver projectModelResolver) {
        super();
        checkNotNull(projectRegistry, "ProjectRegistry must not be null");
        checkNotNull(projectModelResolver, "ProjectModelResolver must not be null");
        this.projectRegistry = projectRegistry;
        this.projectModelResolver = projectModelResolver;
    }

    @Override
    public String getName() {
        return NAME;
    }

    public void setTestSupport(TestSupport testSupport) {
        this.testSupport = testSupport;
    }

    @Nullable
    @Override
    protected List<DependentBinariesResolvedResult> resolveDependents(BinarySpecInternal target) {
        if (!(target instanceof NativeBinarySpecInternal)) {
            return null;
        }
        return resolveDependentBinaries((NativeBinarySpecInternal) target);
    }

    private List<DependentBinariesResolvedResult> resolveDependentBinaries(NativeBinarySpecInternal target) {
        State state = getState();
        return buildResolvedResult(target, state);
    }

    private State getState() {
        try {
            return stateCache.get("state", new Callable<State>() {
                @Override
                public State call() {
                    return buildState();
                }
            });
        } catch (ExecutionException ex) {
            throw new RuntimeException("Unable to build native dependent binaries resolution cache", ex);
        }
    }

    private State buildState() {
        State state = new State();

        List<ProjectInternal> orderedProjects = Ordering.usingToString().sortedCopy(projectRegistry.getAllProjects());
        for (ProjectInternal project : orderedProjects) {
            if (project.getPlugins().hasPlugin(ComponentModelBasePlugin.class)) {
                ModelRegistry modelRegistry = projectModelResolver.resolveProjectModel(project.getPath());
                ModelMap<NativeComponentSpec> components = modelRegistry.realize("components", ModelTypes.modelMap(NativeComponentSpec.class));
                for (NativeBinarySpecInternal binary : allBinariesOf(components.withType(VariantComponentSpec.class))) {
                    state.registerBinary(binary);
                }
                ModelMap<Object> testSuites = modelRegistry.find("testSuites", ModelTypes.modelMap(Object.class));
                if (testSuites != null) {
                    for (NativeBinarySpecInternal binary : allBinariesOf(testSuites.withType(NativeComponentSpec.class).withType(VariantComponentSpec.class))) {
                        state.registerBinary(binary);
                    }
                }
            }
        }

        for (NativeBinarySpecInternal nativeBinary : state.dependencies.keySet()) {
            for (NativeLibraryBinary libraryBinary : nativeBinary.getDependentBinaries()) {
                // Skip prebuilt libraries
                if (libraryBinary instanceof NativeBinarySpecInternal) {
                    // Unfortunate cast! see LibraryBinaryLocator
                    state.dependencies.get(nativeBinary).add((NativeBinarySpecInternal) libraryBinary);
                }
            }
            if (testSupport != null) {
                state.dependencies.get(nativeBinary).addAll(testSupport.getTestDependencies(nativeBinary));
            }
        }

        return state;
    }

    @Override
    protected boolean isTestSuite(BinarySpecInternal target) {
        return testSupport != null && testSupport.isTestSuite(target);
    }

    private List<NativeBinarySpecInternal> allBinariesOf(ModelMap<VariantComponentSpec> components) {
        List<NativeBinarySpecInternal> binaries = Lists.newArrayList();
        for (VariantComponentSpec nativeComponent : components) {
            for (NativeBinarySpecInternal nativeBinary : nativeComponent.getBinaries().withType(NativeBinarySpecInternal.class)) {
                binaries.add(nativeBinary);
            }
        }
        return binaries;
    }

    private List<DependentBinariesResolvedResult> buildResolvedResult(final NativeBinarySpecInternal target, State state) {
        Deque<NativeBinarySpecInternal> stack = new ArrayDeque<NativeBinarySpecInternal>();
        return doBuildResolvedResult(target, state, stack);
    }

    private List<DependentBinariesResolvedResult> doBuildResolvedResult(final NativeBinarySpecInternal target, State state, Deque<NativeBinarySpecInternal> stack) {
        if (stack.contains(target)) {
            onCircularDependencies(state, stack, target);
        }
        List<DependentBinariesResolvedResult> result = resultsCache.getIfPresent(target);
        if (result != null) {
            return result;
        }
        stack.push(target);
        result = Lists.newArrayList();
        List<NativeBinarySpecInternal> dependents = state.getDependents(target);
        for (NativeBinarySpecInternal dependent : dependents) {
            List<DependentBinariesResolvedResult> children = doBuildResolvedResult(dependent, state, stack);
            result.add(new DefaultDependentBinariesResolvedResult(dependent.getId(), dependent.getProjectScopedName(), dependent.isBuildable(), isTestSuite(dependent), children));
        }
        stack.pop();
        resultsCache.put(target, result);
        return result;
    }

    private void onCircularDependencies(final State state, final Deque<NativeBinarySpecInternal> stack, NativeBinarySpecInternal target) {
        GraphNodeRenderer<NativeBinarySpecInternal> nodeRenderer = new GraphNodeRenderer<NativeBinarySpecInternal>() {
            @Override
            public void renderTo(NativeBinarySpecInternal node, StyledTextOutput output) {
                String name = DependentComponentsUtils.getBuildScopedTerseName(node.getId());
                output.withStyle(StyledTextOutput.Style.Identifier).text(name);
            }
        };
        DirectedGraph<NativeBinarySpecInternal, Object> directedGraph = new DirectedGraph<NativeBinarySpecInternal, Object>() {
            @Override
            public void getNodeValues(NativeBinarySpecInternal node, Collection<? super Object> values, Collection<? super NativeBinarySpecInternal> connectedNodes) {
                for (NativeBinarySpecInternal binary : stack) {
                    if (state.getDependents(node).contains(binary)) {
                        connectedNodes.add(binary);
                    }
                }
            }
        };
        DirectedGraphRenderer<NativeBinarySpecInternal> graphRenderer = new DirectedGraphRenderer<NativeBinarySpecInternal>(nodeRenderer, directedGraph);
        StringWriter writer = new StringWriter();
        graphRenderer.renderTo(target, writer);
        throw new CircularReferenceException(String.format("Circular dependency between the following binaries:%n%s", writer.toString()));
    }
}
