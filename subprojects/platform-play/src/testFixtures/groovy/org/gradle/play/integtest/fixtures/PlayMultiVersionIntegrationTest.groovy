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

package org.gradle.play.integtest.fixtures

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult

@TargetCoverage({ PlayCoverage.DEFAULT })
abstract class PlayMultiVersionIntegrationTest extends MultiVersionIntegrationSpec {

    static boolean isPlay22(def version) {
        return version.toString().startsWith('2.2')
    }

    protected ExecutionFailure fails(String... tasks) {
        if (isPlay22(version)) {
            executer.expectDeprecationWarning()
        }
        return super.fails(tasks)
    }

    protected ExecutionResult succeeds(String... tasks) {
        if (isPlay22(version)) {
            executer.expectDeprecationWarning()
        }
        return super.succeeds(tasks)
    }
}
