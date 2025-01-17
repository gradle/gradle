/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.docs.samples;

import org.gradle.exemplar.test.normalizer.FileSeparatorOutputNormalizer;
import org.gradle.exemplar.test.normalizer.GradleOutputNormalizer;
import org.gradle.exemplar.test.normalizer.JavaObjectSerializationOutputNormalizer;
import org.gradle.exemplar.test.runner.SampleModifiers;
import org.gradle.exemplar.test.runner.SamplesOutputNormalizers;
import org.gradle.integtests.fixtures.executer.DependencyReplacingSampleModifier;
import org.gradle.integtests.fixtures.executer.MoreMemorySampleModifier;
import org.gradle.integtests.fixtures.logging.ArtifactResolutionOmittingOutputNormalizer;
import org.gradle.integtests.fixtures.logging.ConfigurationCacheOutputCleaner;
import org.gradle.integtests.fixtures.logging.ConfigurationCacheOutputNormalizer;
import org.gradle.integtests.fixtures.logging.DependencyInsightOutputNormalizer;
import org.gradle.integtests.fixtures.logging.EmbeddedKotlinOutputNormalizer;
import org.gradle.integtests.fixtures.logging.EmptyLineTrimmerOutputNormalizer;
import org.gradle.integtests.fixtures.logging.GradleWelcomeOutputNormalizer;
import org.gradle.integtests.fixtures.logging.NativeComponentReportOutputNormalizer;
import org.gradle.integtests.fixtures.logging.PlatformInOutputNormalizer;
import org.gradle.integtests.fixtures.logging.SampleOutputNormalizer;
import org.gradle.integtests.fixtures.logging.SpringBootWebAppTestOutputNormalizer;
import org.gradle.integtests.fixtures.logging.ZincScalaCompilerOutputNormalizer;
import org.gradle.integtests.fixtures.mirror.SetMirrorsSampleModifier;

@SamplesOutputNormalizers({
    SampleOutputNormalizer.class,
    JavaObjectSerializationOutputNormalizer.class,
    FileSeparatorOutputNormalizer.class,
    GradleWelcomeOutputNormalizer.class,
    GradleOutputNormalizer.class,
    ArtifactResolutionOmittingOutputNormalizer.class,
    NativeComponentReportOutputNormalizer.class,
    DependencyInsightOutputNormalizer.class,
    ConfigurationCacheOutputCleaner.class,
    ConfigurationCacheOutputNormalizer.class,
    EmbeddedKotlinOutputNormalizer.class,
    ZincScalaCompilerOutputNormalizer.class,
    PlatformInOutputNormalizer.class,
    SpringBootWebAppTestOutputNormalizer.class,
    EmptyLineTrimmerOutputNormalizer.class
})
@SampleModifiers({
    SetMirrorsSampleModifier.class,
    MoreMemorySampleModifier.class,
    DependencyReplacingSampleModifier.class
})
/*
 * To run the samples tests:
 *
 * Say you want to run the snippet found at:
 *    src/snippets/dependencyManagement/customizingResolution-consistentResolution
 *
 * then you can run the following command line:
 *
 * ./gradlew :docs:docsTest --tests "*.snippet-dependency-management-customizing-resolution-consistent-resolution*"
 *
 * which would run both Groovy and Kotlin tests.
 */
abstract class BaseSamplesTest {
}
