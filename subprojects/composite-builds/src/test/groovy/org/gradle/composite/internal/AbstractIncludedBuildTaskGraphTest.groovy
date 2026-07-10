/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.composite.internal

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.BuildWorkGraphController
import org.gradle.internal.build.IncludedBuildState
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

abstract class AbstractIncludedBuildTaskGraphTest extends ConcurrentSpec {
    def buildStateRegistry = Mock(BuildStateRegistry)

    BuildState build(BuildIdentifier id, BuildWorkGraphController workGraph = null) {
        def build = Mock(IncludedBuildState)
        _ * build.buildIdentifier >> id
        _ * build.workGraph >> (workGraph ?: Stub(BuildWorkGraphController))
        _ * buildStateRegistry.getBuild(id) >> build
        return build
    }

    static TaskIdentifier taskIdentifier(BuildIdentifier id, String taskPath) {
        return TaskIdentifier.of(id, taskPath)
    }

}
