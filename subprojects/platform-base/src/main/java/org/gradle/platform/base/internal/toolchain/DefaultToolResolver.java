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

import java.util.Set;

public class DefaultToolResolver implements ToolResolver {
    @SuppressWarnings("rawtypes")
    private Set<ToolChainInternal> toolChains;

    public DefaultToolResolver(@SuppressWarnings("rawtypes") ToolChainInternal... toolChains) {
        this.toolChains = Sets.newHashSet(toolChains);
    }

    @Override
    public ToolSearchResult checkToolAvailability(Platform requirement) {
        ToolSearchFailure notAvailableResult = new ToolSearchFailure("No tool chains can satisfy the requirement");
        for (@SuppressWarnings("rawtypes") ToolChainInternal toolChain : toolChains) {
            @SuppressWarnings("unchecked") ToolSearchResult result = toolChain.select(requirement);
            if (result.isAvailable()) {
                return result;
            } else {
                notAvailableResult.addResult(result);
            }
        }
        return notAvailableResult;
    }

    @Override
    public <T> ResolvedTool<T> resolve(Class<T> toolType, Platform requirement) {
        ResolvedToolSearchFailure<T> notAvailableResult = new ResolvedToolSearchFailure<T>(String.format("No tool chains can provide a tool of type %s", toolType.getSimpleName()));
        for (@SuppressWarnings("rawtypes") ToolChainInternal toolChain : toolChains) {
            @SuppressWarnings("unchecked") ToolProvider provider = toolChain.select(requirement);
            if (provider.isAvailable()) {
                return new DefaultResolvedTool<T>(provider, toolType);
            } else {
                notAvailableResult.addResult(provider);
            }
        }
        return notAvailableResult;
    }

    @Override
    public <C extends CompileSpec> ResolvedTool<Compiler<C>> resolveCompiler(Class<C> specType, Platform requirement) {
        CompilerSearchFailure<C> notAvailableResult = new CompilerSearchFailure<C>(String.format("No tool chains can provide a compiler for type %s", specType.getSimpleName()));
        for (@SuppressWarnings("rawtypes") ToolChainInternal toolChain : toolChains) {
            @SuppressWarnings("unchecked") ToolProvider provider = toolChain.select(requirement);
            if (provider.isAvailable()) {
                return new DefaultResolvedCompiler<C>(provider, specType);
            } else {
                notAvailableResult.addResult(provider);
            }
        }
        return notAvailableResult;
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
