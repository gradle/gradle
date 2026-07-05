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
package org.gradle.launcher.daemon.connection;

import org.gradle.launcher.exec.BuildActionResult;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link DaemonBuildResult} for an in-JVM caller: it carries the full {@link BuildActionResult} so the CLI
 * and Tooling API can re-hydrate the build outcome (result payload or failure) exactly as before. The build
 * action result stays off the {@link DaemonBuildResult} interface so the contract is not tied to a JVM type.
 */
@NullMarked
public final class JvmBuildResult implements DaemonBuildResult {
    private final BuildActionResult buildActionResult;

    public JvmBuildResult(BuildActionResult buildActionResult) {
        this.buildActionResult = buildActionResult;
    }

    public BuildActionResult getBuildActionResult() {
        return buildActionResult;
    }

    @Override
    public boolean isSuccessful() {
        return !buildActionResult.hasFailure();
    }

    @Override
    public boolean wasCancelled() {
        return buildActionResult.wasCancelled();
    }
}
