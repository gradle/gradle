/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.samples;

import org.gradle.samples.model.Command;
import org.gradle.samples.model.Sample;
import org.gradle.samples.test.runner.SampleModifier;

import java.util.ArrayList;
import java.util.List;

public class FailOnDeprecationSampleModifier implements SampleModifier {
    @Override
    public Sample modify(Sample sample) {
        if (sample.getId().startsWith("play_")) {
            return sample;
        }
        List<Command> commands = sample.getCommands();
        List<Command> modifiedCommands = new ArrayList<>();
        for (Command command : commands) {
            if ("gradle".equals(command.getExecutable())) {
                List<String> flags = new ArrayList<>(command.getFlags());
                if (flags.stream().noneMatch(flag -> flag.startsWith("--warning-mode"))) {
                    flags.add("--warning-mode=fail");
                }
                modifiedCommands.add(command.toBuilder().setFlags(flags).build());
            } else {
                modifiedCommands.add(command);
            }
        }
        return new Sample(sample.getId(), sample.getProjectDir(), modifiedCommands);
    }
}
