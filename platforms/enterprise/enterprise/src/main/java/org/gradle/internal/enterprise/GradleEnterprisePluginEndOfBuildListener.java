/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise;

import org.gradle.operations.problems.Failure;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Used to signal the end of build to the plugin.
 * <p>
 * Uses a specific listener to guarantee being invoked after user buildFinished callbacks.
 * Expected to be invoked once for a build tree.
 * <p>
 * Implemented by the Enterprise plugin.
 */
public interface GradleEnterprisePluginEndOfBuildListener {

    interface BuildResult {
        /**
         * The failure of the build when the build failed.
         *
         * @deprecated Use {@link #getBuildFailure()} instead.
         */
        @Deprecated
        @Nullable
        Throwable getFailure();

        /**
         * The build failure in a more structured form.
         * <p>
         * {@code null} if the build did not fail.
         *
         * @since 9.0
         */
        @Nullable
        BuildFailure getBuildFailure();
    }

    interface BuildFailure {
        /**
         * The non-empty list of failures.
         *
         * @since 9.0
         */
        List<Failure> getFailures();
    }

    void buildFinished(BuildResult buildResult);

}
