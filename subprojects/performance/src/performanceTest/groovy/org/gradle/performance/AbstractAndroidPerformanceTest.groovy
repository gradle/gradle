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

package org.gradle.performance

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.GradleExecuter

@CompileStatic
abstract class AbstractAndroidPerformanceTest extends AbstractCrossVersionPerformanceTest {
    def setup() {
        runner.args = ['-Dcom.android.build.gradle.overrideVersionCheck=true']
        runner.executerDecorator = { GradleExecuter executor ->
            // todo: investigate why Android builds require this check disabled
            executor.withEagerClassLoaderCreationCheckDisabled()
                .noDeprecationChecks()
        }
    }
}
