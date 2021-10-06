/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.tasks.compile;

import com.google.common.collect.ImmutableList;
import com.google.turbine.options.TurbineOptions;
import org.gradle.workers.WorkAction;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public abstract class TurbineCompileAction implements WorkAction<TurbineWorkParameters> {
    @Override
    public void execute() {
        TurbineWorkParameters parameters = getParameters();
        String[] lines = System.getProperty("java.class.path").split(":");
        Arrays.sort(lines);
        for (String line : lines) {
            System.out.println(line);
        }

        TurbineOptions options = TurbineOptions.builder()
            .setClassPath(ImmutableList.copyOf(parameters.getClasspath().getFiles().stream().map(File::getAbsolutePath).collect(Collectors.toList())))
            .setSources(ImmutableList.copyOf(parameters.getSources().getAsFileTree().getFiles().stream().map(File::getAbsolutePath).collect(Collectors.toList())))
            .setOutput(parameters.getOutput().get().getAsFile().getAbsolutePath())
            .build();

        try {
            com.google.turbine.main.Main.compile(options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
