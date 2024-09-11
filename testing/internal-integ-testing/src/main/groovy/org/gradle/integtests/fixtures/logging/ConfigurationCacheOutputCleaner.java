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

package org.gradle.integtests.fixtures.logging;

import org.gradle.exemplar.executor.ExecutionMetadata;
import org.gradle.exemplar.test.normalizer.OutputNormalizer;

import java.util.regex.Pattern;

/**
 * Conditionally removes the configuration cache logging from the Gradle output.
 * This normalization is only enabled if {@link #CLEAN_CONFIGURATION_CACHE_OUTPUT}
 * system property is set to {@code true}.
 * <p>
 * Most of our samples that aren't aiming to test the configuration cache itself, do not care about
 * its output much. This normalizer allows to reuse the same "golden" output files when testing such
 * samples with the configuration cache enabled.
 */
public class ConfigurationCacheOutputCleaner implements OutputNormalizer {
    // The configuration of the normalizers is static (defined in the annotations), so this
    // normalizer is going to be applied everywhere. We don't want, however, to remove configuration
    // cache output from the tests that check this very output, thus this switch exists.
    private static final String CLEAN_CONFIGURATION_CACHE_OUTPUT = "org.gradle.integtest.samples.cleanConfigurationCacheOutput";
    private static final Pattern CONFIGURATION_CACHE_OUTPUT = Pattern.compile(
        "((Configuration cache entry[^\\n]+\\.)|" +
            "(See the complete report at [^\\n]+\\n)|" +
            "(0 problems were found storing the configuration cache.\\n))(\\n)?"
    );

    @Override
    public String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        if (!Boolean.parseBoolean(System.getProperty(CLEAN_CONFIGURATION_CACHE_OUTPUT))) {
            return commandOutput;
        }
        return CONFIGURATION_CACHE_OUTPUT.matcher(commandOutput).replaceAll("");
    }
}
