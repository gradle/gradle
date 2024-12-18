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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.IdFactory;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;

public final class DefaultIdFactory extends IdFactory {

    public static final DefaultIdFactory INSTANCE = new DefaultIdFactory();

    @Override
    public ProblemGroup createRootProblemGroup(String name, String displayName) {
        return new DefaultProblemGroup(name, displayName);
    }

    @Override
    public ProblemGroup createProblemGroup(String name, String displayName, ProblemGroup parent) {
        return new DefaultProblemGroup(name, displayName, parent);
    }

    @Override
    public ProblemId createProblemId(String name, String displayName, ProblemGroup group) {
        return new DefaultProblemId(name, displayName, group);
    }
}
