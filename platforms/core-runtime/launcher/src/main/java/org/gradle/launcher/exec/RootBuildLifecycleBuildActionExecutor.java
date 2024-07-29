/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.exec;

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeActionExecutor;
import org.gradle.internal.buildtree.BuildTreeContext;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.jvm.SupportedJavaVersions;

public class RootBuildLifecycleBuildActionExecutor implements BuildTreeActionExecutor {

    private final BuildActionRunner buildActionRunner;
    private final BuildStateRegistry buildStateRegistry;

    public RootBuildLifecycleBuildActionExecutor(BuildStateRegistry buildStateRegistry,
                                                 BuildActionRunner buildActionRunner) {
        this.buildActionRunner = buildActionRunner;
        this.buildStateRegistry = buildStateRegistry;
    }

    @Override
    public BuildActionRunner.Result execute(BuildAction action, BuildTreeContext buildTreeContext) {

        int currentMajor = Integer.parseInt(JavaVersion.current().getMajorVersion());
        if (currentMajor < SupportedJavaVersions.FUTURE_MINIMUM_JAVA_VERSION) {
            DeprecationLogger.deprecateAction("Executing Gradle on JVM versions 16 and lower")
                .withContext("Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains.")
                .willBecomeAnErrorInGradle9()
                .withUpgradeGuideSection(8, "minimum_daemon_jvm_version")
                .nagUser();
        }

        RootBuildState rootBuild = buildStateRegistry.createRootBuild(BuildDefinition.fromStartParameter(action.getStartParameter(), null));
        return rootBuild.run(buildController -> buildActionRunner.run(action, buildController));
    }
}
