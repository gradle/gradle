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

package org.gradle.internal.buildtree;

import org.jspecify.annotations.NullMarked;

/**
 * A failure that resilient model building hid behind a partial model result returned to the client. It travels back
 * with that result so the build can still fail once model building finishes. See {@link BuildTreeModelCreator}.
 */
@NullMarked
public final class ResilientModelFailure {

    private final Throwable failure;
    private final boolean configurationFailure;

    private ResilientModelFailure(Throwable failure, boolean configurationFailure) {
        this.failure = failure;
        this.configurationFailure = configurationFailure;
    }

    /**
     * A tooling model builder threw after its target project had configured successfully.
     */
    public static ResilientModelFailure ofModelBuilder(Throwable failure) {
        return new ResilientModelFailure(failure, false);
    }

    /**
     * A project or the build itself failed to configure.
     */
    public static ResilientModelFailure ofConfiguration(Throwable failure) {
        return new ResilientModelFailure(failure, true);
    }

    public Throwable getFailure() {
        return failure;
    }

    public boolean isConfigurationFailure() {
        return configurationFailure;
    }
}
