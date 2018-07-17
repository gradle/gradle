/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.logging.ArtifactResolutionOmittingOutputNormalizer
import org.gradle.integtests.fixtures.logging.NativeComponentReportOutputNormalizer
import org.gradle.integtests.fixtures.logging.PlayComponentReportOutputNormalizer
import org.gradle.integtests.fixtures.logging.SampleOutputNormalizer
import org.gradle.samples.test.normalizer.FileSeparatorOutputNormalizer
import org.gradle.samples.test.normalizer.JavaObjectSerializationOutputNormalizer
import org.gradle.samples.test.runner.GradleSamplesRunner
import org.gradle.samples.test.runner.SamplesOutputNormalizers
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.runner.RunWith

@Requires(TestPrecondition.JDK8_OR_LATER)
@RunWith(GradleSamplesRunner.class)
@SamplesOutputNormalizers([
    JavaObjectSerializationOutputNormalizer.class,
    SampleOutputNormalizer.class,
    FileSeparatorOutputNormalizer.class,
    ArtifactResolutionOmittingOutputNormalizer.class,
    NativeComponentReportOutputNormalizer.class,
    PlayComponentReportOutputNormalizer.class
])
class UserGuideSamplesIntegrationTest {
    /*
    Important info: This test uses Exemplar (https://github.com/gradle/exemplar/) to discover and check samples.

    In order to add a new sample:
        * Create your new sample project in a subdirectory under subprojects/docs/samples/
        * Write a *.sample.conf HOCON file in the root of your sample project dir
        * Exemplar will automatically discover your sample. See instructions below for running it

    To update a sample test, change the *.sample.conf file

     You can run all samples tests with
        ./gradlew :integtest:integTest --tests "UserGuideSamplesIntegrationTest"

     To run a subset of samples, use a more fine-grained test filter like
        ./gradlew :integtest:integTest --tests "UserGuideSamplesIntegrationTest.*native*"
    */
}
