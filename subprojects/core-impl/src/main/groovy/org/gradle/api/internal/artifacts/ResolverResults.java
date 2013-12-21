/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.result.ResolutionResult;

public class ResolverResults {

    private final ResolvedConfiguration resolvedConfiguration;
    private final ResolutionResult resolutionResult;
    private final ResolveException fatalFailure;

    public ResolverResults(ResolvedConfiguration resolvedConfiguration, ResolveException fatalFailure) {
        this(resolvedConfiguration, null, fatalFailure);
    }

    public ResolverResults(ResolvedConfiguration resolvedConfiguration, ResolutionResult resolutionResult) {
        this(resolvedConfiguration, resolutionResult, null);
    }

    private ResolverResults(ResolvedConfiguration resolvedConfiguration, ResolutionResult resolutionResult, ResolveException fatalFailure) {
        this.resolvedConfiguration = resolvedConfiguration;
        this.resolutionResult = resolutionResult;
        this.fatalFailure = fatalFailure;
    }

    //old model, slowly being replaced by the new model
    public ResolvedConfiguration getResolvedConfiguration() {
        return resolvedConfiguration;
    }

    //new model
    public ResolutionResult getResolutionResult() {
        if (fatalFailure != null) {
            throw fatalFailure;
        }
        return resolutionResult;
    }

    public ResolverResults withResolvedConfiguration(ResolvedConfiguration resolvedConfiguration) {
        return new ResolverResults(resolvedConfiguration, resolutionResult);
    }
}