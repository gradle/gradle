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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.BuildTree.class)
public class DefaultBuildSessionProblemContainer implements BuildSessionProblemContainer {

    private final Multimap<Throwable, Problem> problemsForThrowables = Multimaps.synchronizedMultimap(HashMultimap.<Throwable, Problem>create());

    @Override
    public void onProblem(Throwable exception, Problem problem) {
        problemsForThrowables.put(exception, problem);
    }

    @Override
    public Multimap<Throwable, Problem> getProblemsForThrowables() {
        return problemsForThrowables;
    }
}
