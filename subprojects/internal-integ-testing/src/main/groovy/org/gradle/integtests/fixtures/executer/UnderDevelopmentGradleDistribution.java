/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.integtests.fixtures.executer;

import org.gradle.test.fixtures.file.TestDirectoryProvider;

public class UnderDevelopmentGradleDistribution extends DefaultGradleDistribution {

    public UnderDevelopmentGradleDistribution() {
        this(IntegrationTestBuildContext.INSTANCE);
    }

    public UnderDevelopmentGradleDistribution(IntegrationTestBuildContext buildContext) {
        super(
            buildContext.getVersion(),
            buildContext.getGradleHomeDir(),
            buildContext.getDistributionsDir().file(String.format("gradle-%s-bin.zip", buildContext.getVersion().getBaseVersion().getVersion()))
        );
    }

    @Override
    public GradleExecuter executer(TestDirectoryProvider testDirectoryProvider, IntegrationTestBuildContext buildContext) {
        return new GradleContextualExecuter(this, testDirectoryProvider, buildContext);
    }
}

