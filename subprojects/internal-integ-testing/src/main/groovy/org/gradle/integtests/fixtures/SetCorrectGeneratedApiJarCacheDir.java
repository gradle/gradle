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

package org.gradle.integtests.fixtures;

import org.gradle.cache.internal.DefaultGeneratedGradleJarCache;
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext;
import org.gradle.samples.model.Command;
import org.gradle.samples.model.Sample;
import org.gradle.samples.test.runner.SampleModifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SetCorrectGeneratedApiJarCacheDir implements SampleModifier {
    @Override
    public Sample modify(Sample sample) {
        List<Command> commands = sample.getCommands();
        List<Command> modifiedCommands = new ArrayList<Command>();
        for (Command command : commands) {
            File generatedApiJarCacheDir = IntegrationTestBuildContext.INSTANCE.getGradleGeneratedApiJarCacheDir();
            if ("gradle".equals(command.getExecutable()) && generatedApiJarCacheDir != null) {
                List<String> args = new ArrayList<String>(command.getArgs());
                args.add(String.format("-D%s=%s", DefaultGeneratedGradleJarCache.BASE_DIR_OVERRIDE_PROPERTY, generatedApiJarCacheDir.getAbsolutePath()));
                modifiedCommands.add(command.toBuilder().setArgs(args).build());
            } else {
                modifiedCommands.add(command);
            }
        }
        return new Sample(sample.getId(), sample.getProjectDir(), modifiedCommands);
    }
}
