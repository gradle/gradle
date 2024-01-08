/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.problems.ProblemReporter;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collections;
import java.util.List;

@ServiceScope(Scopes.BuildTree.class)
public class DefaultProblems implements InternalProblems {

    private final ProblemEmitter emitter;
    private final List<ProblemTransformer> transformers;
    private final InternalProblemReporter internalReporter;

    public DefaultProblems(ProblemEmitter emitter) {
        this(emitter, Collections.<ProblemTransformer>emptyList());
    }

    public DefaultProblems(ProblemEmitter emitter, List<ProblemTransformer> transformers) {
        this.emitter = emitter;
        this.transformers = transformers;
        internalReporter = createReporter(DefaultProblemCategory.GRADLE_CORE_NAMESPACE, emitter, transformers);
    }

    @Override
    public ProblemReporter forNamespace(String namespace) {
        if (DefaultProblemCategory.GRADLE_CORE_NAMESPACE.equals(namespace)) {
            throw new IllegalStateException("Cannot use " + DefaultProblemCategory.GRADLE_CORE_NAMESPACE + " namespace.");
        }
        return createReporter(namespace, emitter, transformers);
    }

    private static DefaultProblemReporter createReporter(String namespace, ProblemEmitter emitter, List<ProblemTransformer> transformers) {
        return new DefaultProblemReporter(emitter, transformers, namespace);
    }

    @Override
    public InternalProblemReporter getInternalReporter() {
        return internalReporter;
    }
}
