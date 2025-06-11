/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.docs.samples.bucket;

import org.gradle.exemplar.test.normalizer.FileSeparatorOutputNormalizer;
import org.gradle.exemplar.test.normalizer.GradleOutputNormalizer;
import org.gradle.exemplar.test.normalizer.JavaObjectSerializationOutputNormalizer;
import org.gradle.exemplar.test.runner.SampleModifiers;
import org.gradle.exemplar.test.runner.SamplesOutputNormalizers;
import org.gradle.integtests.fixtures.executer.DependencyReplacingSampleModifier;
import org.gradle.integtests.fixtures.executer.MoreMemorySampleModifier;
import org.gradle.integtests.fixtures.executer.RemoveDeadEndCacheServerModifier;
import org.gradle.integtests.fixtures.logging.ArtifactResolutionOmittingOutputNormalizer;
import org.gradle.integtests.fixtures.logging.ConfigurationCacheOutputCleaner;
import org.gradle.integtests.fixtures.logging.ConfigurationCacheOutputNormalizer;
import org.gradle.integtests.fixtures.logging.DependencyInsightOutputNormalizer;
import org.gradle.integtests.fixtures.logging.EmbeddedKotlinOutputNormalizer;
import org.gradle.integtests.fixtures.logging.EmptyLineRemovalOutputNormalizer;
import org.gradle.integtests.fixtures.logging.EmptyLineTrimmerOutputNormalizer;
import org.gradle.integtests.fixtures.logging.GradleWelcomeOutputNormalizer;
import org.gradle.integtests.fixtures.logging.NativeComponentReportOutputNormalizer;
import org.gradle.integtests.fixtures.logging.PlatformInOutputNormalizer;
import org.gradle.integtests.fixtures.logging.RepositoryMirrorOutputNormalizer;
import org.gradle.integtests.fixtures.logging.SampleOutputNormalizer;
import org.gradle.integtests.fixtures.logging.SpringBootWebAppTestOutputNormalizer;
import org.gradle.integtests.fixtures.logging.ZincScalaCompilerOutputNormalizer;
import org.gradle.integtests.fixtures.mirror.SetMirrorsSampleModifier;

/**
 * What are these empty classes? Please read the doc in {@link org.gradle.docs.samples.SamplesTestEngine}.
 */
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
    EmptyLineTrimmerOutputNormalizer.class,
    RepositoryMirrorOutputNormalizer.class,
    EmptyLineRemovalOutputNormalizer.class,
})
@SampleModifiers({
    SetMirrorsSampleModifier.class,
    MoreMemorySampleModifier.class,
    DependencyReplacingSampleModifier.class,
    RemoveDeadEndCacheServerModifier.class,
})
public class Buckets {
    public static final int BUCKET_NUMBER = 50;

    static {
        for (int i = 1; i <= BUCKET_NUMBER; i++) {
            try {
                Class.forName(Buckets.class.getName().replace("Buckets", "Bucket" + i));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

class Bucket1 {}

class Bucket2 {}

class Bucket3 {}

class Bucket4 {}

class Bucket5 {}

class Bucket6 {}

class Bucket7 {}

class Bucket8 {}

class Bucket9 {}

class Bucket10 {}

class Bucket11 {}

class Bucket12 {}

class Bucket13 {}

class Bucket14 {}

class Bucket15 {}

class Bucket16 {}

class Bucket17 {}

class Bucket18 {}

class Bucket19 {}

class Bucket20 {}

class Bucket21 {}

class Bucket22 {}

class Bucket23 {}

class Bucket24 {}

class Bucket25 {}

class Bucket26 {}

class Bucket27 {}

class Bucket28 {}

class Bucket29 {}

class Bucket30 {}

class Bucket31 {}

class Bucket32 {}

class Bucket33 {}

class Bucket34 {}

class Bucket35 {}

class Bucket36 {}

class Bucket37 {}

class Bucket38 {}

class Bucket39 {}

class Bucket40 {}

class Bucket41 {}

class Bucket42 {}

class Bucket43 {}

class Bucket44 {}

class Bucket45 {}

class Bucket46 {}

class Bucket47 {}

class Bucket48 {}

class Bucket49 {}

class Bucket50 {}
