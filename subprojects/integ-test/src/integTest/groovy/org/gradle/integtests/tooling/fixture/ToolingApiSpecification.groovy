/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.tooling.fixture

import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

abstract class ToolingApiSpecification extends Specification {
    @Rule public final SetSystemProperties sysProperties = new SetSystemProperties()
    @Rule public final GradleDistribution dist = new GradleDistribution()
    public final ToolingApi toolingApi = new ToolingApi(dist)

    String optionalTargetDist = null

    BasicGradleDistribution getTargetDist() {
        optionalTargetDist? dist.previousVersion(optionalTargetDist) : dist
    }

    @Rule public final targetDistSelector = new TargetDistSelector()

    void setup() {
        toolingApi.withConnector {
            it.useInstallation(new File(targetDist.gradleHomeDir.absolutePath))
            if (!(targetDist instanceof GradleDistribution)) {
                it.embedded = false
            }
        }
    }

    def withConnection(Closure cl) {
        toolingApi.withConnection(cl)
    }

    def maybeFailWithConnection(Closure cl) {
        toolingApi.maybeFailWithConnection(cl)
    }
}