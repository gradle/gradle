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

import org.gradle.cache.internal.DefaultGeneratedGradleJarCache
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.MoreMemorySampleModifier
import org.gradle.integtests.fixtures.logging.ArtifactResolutionOmittingOutputNormalizer
import org.gradle.integtests.fixtures.logging.DependencyInsightOutputNormalizer
import org.gradle.integtests.fixtures.logging.NativeComponentReportOutputNormalizer
import org.gradle.integtests.fixtures.logging.PlayComponentReportOutputNormalizer
import org.gradle.integtests.fixtures.logging.SampleOutputNormalizer
import org.gradle.integtests.fixtures.mirror.SetMirrorsSampleModifier
import org.gradle.samples.test.normalizer.FileSeparatorOutputNormalizer
import org.gradle.samples.test.normalizer.JavaObjectSerializationOutputNormalizer
import org.gradle.samples.test.runner.GradleSamplesRunner
import org.gradle.samples.test.runner.SampleModifiers
import org.gradle.samples.test.runner.SamplesOutputNormalizers
import org.gradle.test.fixtures.file.TestFile
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith

@RunWith(GradleSamplesRunner.class)
@SamplesOutputNormalizers([
        JavaObjectSerializationOutputNormalizer,
        SampleOutputNormalizer,
        FileSeparatorOutputNormalizer,
        ArtifactResolutionOmittingOutputNormalizer,
        NativeComponentReportOutputNormalizer,
        PlayComponentReportOutputNormalizer,
        DependencyInsightOutputNormalizer
])
@SampleModifiers([
    SetMirrorsSampleModifier,
    MoreMemorySampleModifier,
    FailOnDeprecationSampleModifier,
])
class UserGuideSamplesIntegrationTest {

    /*
    Important info: This test uses Exemplar (https://github.com/gradle/exemplar/) to discover and check samples.

    In order to add a new sample:
        * Create your new sample project in a subdirectory under subprojects/docs/src/snippets/
        * Write a *.sample.conf HOCON file in the root of your sample project dir
        * Exemplar will automatically discover your sample. See instructions below for running it

    To update a sample test, change the *.sample.conf file

     You can run all samples tests with
        ./gradlew :samples:integTest --tests "org.gradle.integtests.samples.UserGuideSamplesIntegrationTest"

     To run a subset of samples, use a more fine-grained test filter like
        ./gradlew :samples:integTest --tests "org.gradle.integtests.samples.UserGuideSamplesIntegrationTest.*native*"
    */

    // NOTE: This weirdness is here because GradleSamplesRunner does not support JUnit @Rule, @After or @Before.
    // Our sample executor needs to be isolated from other TestKit-based tests that may run in the Gradle CI pipeline with a "partial" distribution
    // Partial distributions have the same version number as a full distribution, but when we generate a Kotlin extensions jar, we'll only include
    // extensions that are defined in the distribution. If we first run with a partial distribution, our samples will fail when trying to use plugins
    // from the full distribution.

    // Previous value of BASE_DIR_OVERRIDE_PROPERTY
    static String previous

    @BeforeClass
    static void before() {
        def buildContext = IntegrationTestBuildContext.INSTANCE
        TestFile generatedApiJarCacheDir = buildContext.getGradleGeneratedApiJarCacheDir()
        if (generatedApiJarCacheDir != null) {
            previous = System.getProperty(DefaultGeneratedGradleJarCache.BASE_DIR_OVERRIDE_PROPERTY)
            System.setProperty(DefaultGeneratedGradleJarCache.BASE_DIR_OVERRIDE_PROPERTY, generatedApiJarCacheDir.getAbsolutePath())
        }
    }

    @AfterClass
    static void after() {
        if (previous) {
            System.setProperty(DefaultGeneratedGradleJarCache.BASE_DIR_OVERRIDE_PROPERTY, previous)
        } else {
            System.clearProperty(DefaultGeneratedGradleJarCache.BASE_DIR_OVERRIDE_PROPERTY)
        }
    }
}
