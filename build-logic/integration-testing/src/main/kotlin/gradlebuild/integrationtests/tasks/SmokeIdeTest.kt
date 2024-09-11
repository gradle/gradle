/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.integrationtests.tasks

import org.gradle.api.tasks.CacheableTask


/**
 * A test that provides an IDE as environment for checking IDE and Gradle behavior during synchronization process.
 * These tests are running using `forking` executor, since current Gradle distribution has to be used as a Gradle version for IDE.
 */
@CacheableTask
abstract class SmokeIdeTest : DistributionTest() {
    override val prefix: String = "smokeIde"
}
