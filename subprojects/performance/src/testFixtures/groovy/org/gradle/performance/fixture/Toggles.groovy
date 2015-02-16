/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.performance.fixture

import org.gradle.model.persist.ReusingModelRegistryStore

class Toggles {

    static BuildSpecification.Builder modelReuse(BuildSpecification.Builder spec) {
        spec.gradleOpts("-D$ReusingModelRegistryStore.TOGGLE=true", "-Dorg.gradle.daemon.performance.expire-at=0")
    }

    static BuildSpecification.Builder noDaemonLogging(BuildSpecification.Builder spec) {
        spec.gradleOpts("-Dorg.gradle.daemon.disable-output=true")
    }

    static BuildSpecification.Builder transformedDsl(BuildSpecification.Builder spec) {
        spec.gradleOpts("-Dorg.gradle.model.dsl=true")
    }

}
