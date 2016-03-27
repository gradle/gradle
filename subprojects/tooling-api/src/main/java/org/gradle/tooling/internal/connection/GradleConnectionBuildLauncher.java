/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.connection;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.connection.BuildIdentity;
import org.gradle.tooling.internal.consumer.CompositeConnectionParameters;
import org.gradle.tooling.internal.consumer.DefaultBuildLauncher;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.model.Launchable;

public class GradleConnectionBuildLauncher extends DefaultBuildLauncher implements BuildLauncher {
    public GradleConnectionBuildLauncher(AsyncConsumerActionExecutor connection, CompositeConnectionParameters parameters) {
        super(connection, parameters);
    }

    @Override
    public void preprocessLaunchables(Iterable<? extends Launchable> launchables) {
        BuildIdentity targetBuildIdentity = null;
        for (Launchable launchable : launchables) {
            BuildIdentity launchableBuildIdentity = launchable.getGradleProjectIdentifier().getBuild();
            if (targetBuildIdentity == null) {
                targetBuildIdentity = launchableBuildIdentity;
            } else if (!targetBuildIdentity.equals(launchableBuildIdentity)) {
                throw new IllegalArgumentException("All Launchables must originate from the same build.");
            }
        }
        operationParamsBuilder.setBuildIdentity(targetBuildIdentity);
    }
}
