//CHECKSTYLE:OFF
package org.gradle.docs.samples;

import org.gradle.integtests.fixtures.FailOnDeprecationSampleModifier;
import org.gradle.integtests.fixtures.executer.MoreMemorySampleModifier;
import org.gradle.integtests.fixtures.logging.ArtifactResolutionOmittingOutputNormalizer;
import org.gradle.integtests.fixtures.logging.DependencyInsightOutputNormalizer;
import org.gradle.integtests.fixtures.logging.NativeComponentReportOutputNormalizer;
import org.gradle.integtests.fixtures.logging.SampleOutputNormalizer;
import org.gradle.integtests.fixtures.logging.PlayComponentReportOutputNormalizer;
import org.gradle.integtests.fixtures.mirror.SetMirrorsSampleModifier;
import org.gradle.samples.test.normalizer.FileSeparatorOutputNormalizer;
import org.gradle.samples.test.normalizer.JavaObjectSerializationOutputNormalizer;
import org.gradle.samples.test.normalizer.GradleOutputNormalizer;
import org.gradle.samples.test.runner.GradleSamplesRunner;
import org.gradle.samples.test.runner.SampleModifiers;
import org.gradle.samples.test.runner.SamplesOutputNormalizers;
import org.gradle.samples.test.runner.SamplesRoot;
import org.junit.runner.RunWith;

@RunWith(GradleSamplesRunner.class)
@SamplesOutputNormalizers({
    SampleOutputNormalizer.class,
    JavaObjectSerializationOutputNormalizer.class,
    FileSeparatorOutputNormalizer.class,
    GradleOutputNormalizer.class,
    ArtifactResolutionOmittingOutputNormalizer.class,
    NativeComponentReportOutputNormalizer.class,
    PlayComponentReportOutputNormalizer.class,
    DependencyInsightOutputNormalizer.class
})
@SampleModifiers({
    SetMirrorsSampleModifier.class,
    MoreMemorySampleModifier.class,
    FailOnDeprecationSampleModifier.class
})
public class ExemplarExternalSamplesFunctionalTest {}
