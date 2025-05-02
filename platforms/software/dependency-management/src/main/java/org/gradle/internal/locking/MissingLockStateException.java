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

package org.gradle.internal.locking;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.DisplayName;
import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.List;

public class MissingLockStateException extends RuntimeException implements ResolutionProvider {

    private static final List<String> RESOLUTIONS;
    static {
        RESOLUTIONS = ImmutableList.of(
            "To create the lock state, run a task that performs dependency resolution and add '--write-locks' to the command line.",
            new DocumentationRegistry().getDocumentationRecommendationFor("information on generating lock state", "dependency_locking", "generating_and_updating_dependency_locks"));
    }
    public MissingLockStateException(DisplayName displayName) {
        super("Locking strict mode: " + displayName.getCapitalizedDisplayName() + " is locked but does not have lock state.");
    }

    @Override
    public List<String> getResolutions() {
        return RESOLUTIONS;
    }
}
