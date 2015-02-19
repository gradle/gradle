/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal.toolchain;

import com.google.common.collect.Sets;

import org.gradle.api.GradleException;
import org.gradle.internal.text.TreeFormatter;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.platform.base.Platform;
import org.gradle.util.TreeVisitor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

public class DefaultToolResolver implements ToolResolver {
    private Set<ToolChainInternal<? extends Platform>> toolChains;

    public DefaultToolResolver() {
        this.toolChains = Sets.newHashSet();
    }

    public void registerToolChain(ToolChainInternal<? extends Platform> toolChain) {
        toolChains.add(toolChain);
    }

    /**
     * Finds the most inherited Platform parameter type of the select method on a toolchain.  It assumes that
     * a ToolChainInternal has only one public declared select method.
     */
    private Class<? extends Platform> getPlatformType(ToolChainInternal<? extends Platform> toolChain) {
        //TODO Do we want to support ToolChains with select methods for multiple platform types?
        Class<?> toolChainClass = toolChain.getClass();
        Class<? extends Platform> platformType = null;
        for (Method method : toolChainClass.getMethods()) {
            if (method.getName().equals("select") && Modifier.isPublic(method.getModifiers())) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1 && Platform.class.isAssignableFrom(parameterTypes[0])) {
                    @SuppressWarnings("unchecked") Class<? extends Platform> converted = (Class<? extends Platform>) parameterTypes[0];
                    // Check to see if this type is more inherited than what has already been found
                    // This filters out any methods from parent classes/interfaces
                    if (platformType == null || platformType.isAssignableFrom(converted)) {
                        platformType = converted;
                    }
                }
            }
        }
        return platformType;
    }

    /**
     * Filters the list of toolchains for only those that support the given platform
     */
    private <P extends Platform> Set<ToolChainInternal<P>> filterToolChains(P platform) {
        Set<ToolChainInternal<P>> platformToolChains = Sets.newHashSet();
        for (ToolChainInternal<? extends Platform> raw : toolChains) {
            if (getPlatformType(raw).isAssignableFrom(platform.getClass())) {
                @SuppressWarnings("unchecked") ToolChainInternal<P> converted = (ToolChainInternal<P>) raw;
                platformToolChains.add(converted);
            }
        }
        return platformToolChains;
    }

    protected <P extends Platform> ToolSearchResult findToolProvider(P requirement) {
        ToolSearchFailure notAvailableResult = new ToolSearchFailure("No tool chains can satisfy the requirement");
        for (ToolChainInternal<P> toolChain : filterToolChains(requirement)) {
            ToolSearchResult result = toolChain.select(requirement);
            if (result.isAvailable()) {
                return result;
            } else {
                notAvailableResult.addResult(result);
            }
        }
        return notAvailableResult;
    }

    @Override
    public <P extends Platform> ToolSearchResult checkToolAvailability(P requirement) {
        return findToolProvider(requirement);
    }

    @Override
    public <T, P extends Platform> ResolvedTool<T> resolve(Class<T> toolType, P requirement) {
        ToolSearchResult toolProvider = findToolProvider(requirement);
        if (toolProvider.isAvailable()) {
            return new DefaultResolvedTool<T>((ToolProvider)toolProvider, toolType);
        } else {
            ResolvedToolSearchFailure<T> notAvailableResult = new ResolvedToolSearchFailure<T>(String.format("No tool chains can provide a tool of type %s", toolType.getSimpleName()));
            notAvailableResult.addResult(toolProvider);
            return notAvailableResult;
        }
    }

    @Override
    public <C extends CompileSpec, P extends Platform> ResolvedTool<Compiler<C>> resolveCompiler(Class<C> specType, P requirement) {
        ToolSearchResult toolProvider = findToolProvider(requirement);
        if (toolProvider.isAvailable()) {
            return new DefaultResolvedCompiler<C>((ToolProvider)toolProvider, specType);
        } else {
            CompilerSearchFailure<C> notAvailableResult = new CompilerSearchFailure<C>(String.format("No tool chains can provide a compiler for type %s", specType.getSimpleName()));
            notAvailableResult.addResult(toolProvider);
            return notAvailableResult;
        }
    }

    private static class ResolvedToolSearchFailure<T> extends ToolSearchFailure implements ResolvedTool<T> {
        public ResolvedToolSearchFailure(String message) {
            super(message);
        }

        @Override
        public T get() {
            throw failure();
        }
    }

    private static class CompilerSearchFailure<T extends CompileSpec> extends ToolSearchFailure implements ResolvedTool<Compiler<T>> {
        public CompilerSearchFailure(String message) {
            super(message);
        }

        @Override
        public Compiler<T> get() {
            throw failure();
        }
    }

    private static class ToolSearchFailure implements ToolSearchResult {
        private final String message;
        Set<ToolSearchResult> results = Sets.newHashSet();

        public ToolSearchFailure(String message) {
            this.message = message;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(message);
            visitor.startChildren();
            for (ToolSearchResult result : results) {
                result.explain(visitor);
            }
            visitor.endChildren();
        }

        RuntimeException failure() {
            TreeFormatter formatter = new TreeFormatter();
            explain(formatter);
            return new GradleException(formatter.toString());
        }

        void addResult(ToolSearchResult result) {
            results.add(result);
        }
    }
}
