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

package org.gradle.docs.samples;


import com.google.common.base.Splitter;
import org.gradle.exemplar.model.Command;
import org.gradle.exemplar.model.Sample;
import org.gradle.exemplar.test.runner.SampleModifier;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OptionalConfigurationCacheOutputSampleModifier implements SampleModifier {
    private static final String ENABLE_CONFIGURATION_CACHE_OUTPUT = "org.gradle.integtest.samples.withConfigurationCache";
    private static final String OPTIONAL_LINE_PREFIX = "?? ";

    @Override
    public Sample modify(Sample sampleIn) {
        if (!hasOutput(sampleIn)) {
            return sampleIn;
        }
        boolean shouldUseOptionalOutput = Boolean.parseBoolean(System.getProperty(ENABLE_CONFIGURATION_CACHE_OUTPUT));
        Function<Command, Command> commandTransformer = shouldUseOptionalOutput ? this::useOptionalConfigurationCacheOutput : this::stripOptionalConfigurationCacheOutput;

        return new Sample(
            sampleIn.getId(),
            sampleIn.getProjectDir(),
            sampleIn.getCommands().stream().map(commandTransformer).collect(Collectors.toList()));
    }

    private boolean hasOutput(Sample sample) {
        for (Command command : sample.getCommands()) {
            if (command.getExpectedOutput() != null) {
                return true;
            }
        }
        return false;
    }

    private Command stripOptionalConfigurationCacheOutput(Command cmd) {
        return toCommandWithTransformedOutput(cmd, line -> null);
    }

    private Command useOptionalConfigurationCacheOutput(Command cmd) {
        return toCommandWithTransformedOutput(cmd, line -> line);
    }

    private Command toCommandWithTransformedOutput(Command cmdIn, Function<String, String> optionalOutputLineTransformer) {
        String expectedOutput = cmdIn.getExpectedOutput();
        if (expectedOutput == null) {
            // No output is specified for this command, no transformation is necessary, use it as is
            return cmdIn;
        }
        Splitter lineSplitter = Splitter.on('\n');
        return cmdIn.toBuilder().setExpectedOutput(lineSplitter.splitToStream(expectedOutput).map(line -> {
            if (line.startsWith(OPTIONAL_LINE_PREFIX)) {
                return optionalOutputLineTransformer.apply(line.substring(OPTIONAL_LINE_PREFIX.length()));
            }
            return line;
        }).filter(Objects::nonNull).collect(Collectors.joining("\n"))).build();
    }
}
