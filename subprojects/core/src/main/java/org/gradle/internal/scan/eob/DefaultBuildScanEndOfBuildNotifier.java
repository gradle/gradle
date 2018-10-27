/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.scan.eob;

import org.gradle.api.Action;
import org.gradle.api.invocation.Gradle;

import javax.annotation.Nullable;

public class DefaultBuildScanEndOfBuildNotifier implements BuildScanEndOfBuildNotifier {

    private final Gradle gradle;
    private boolean registered;

    public DefaultBuildScanEndOfBuildNotifier(Gradle gradle) {
        this.gradle = gradle;
    }

    @Override
    public void notify(final Listener listener) {
        if (registered) {
            throw new IllegalStateException("Listener already registered");
        }

        registered = true;
        gradle.buildFinished(new Action<org.gradle.BuildResult>() {
            @Override
            public void execute(final org.gradle.BuildResult buildResult) {
                listener.execute(new BuildResult() {
                    @Nullable
                    @Override
                    public Throwable getFailure() {
                        return buildResult.getFailure();
                    }
                });
            }
        });
    }

}
