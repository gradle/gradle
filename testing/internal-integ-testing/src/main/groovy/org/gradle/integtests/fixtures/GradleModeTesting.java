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

package org.gradle.integtests.fixtures;

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter;
import org.jspecify.annotations.NullMarked;

import java.util.function.BooleanSupplier;

/**
 * A Gradle execution mode that integration tests can be gated on
 * (e.g. Configuration Cache, Isolated Projects).
 */
@NullMarked
public enum GradleModeTesting {

    CONFIGURATION_CACHE("Configuration Cache", GradleContextualExecuter::isConfigCache),
    ISOLATED_PROJECTS("Isolated Projects", GradleContextualExecuter::isIsolatedProjects);

    private final String displayName;
    private final BooleanSupplier activeCheck;

    GradleModeTesting(String displayName, BooleanSupplier activeCheck) {
        this.displayName = displayName;
        this.activeCheck = activeCheck;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Whether this mode is the one currently being exercised by the contextual executer
     * for the test running on this thread. Delegates to {@link GradleContextualExecuter}.
     */
    public boolean isActive() {
        return activeCheck.getAsBoolean();
    }
}
