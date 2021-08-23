/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.mirror;

import org.gradle.integtests.fixtures.RepoScriptBlockUtil;
import org.gradle.exemplar.model.Command;
import org.gradle.exemplar.model.Sample;
import org.gradle.exemplar.test.runner.SampleModifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.api.internal.artifacts.BaseRepositoryFactory.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY;
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.gradlePluginRepositoryMirrorUrl;

public class SetMirrorsSampleModifier implements SampleModifier {

    private final File initScript = RepoScriptBlockUtil.createMirrorInitScript();

    @Override
    public Sample modify(Sample sample) {
        if (sample.getId().contains("usePluginsInInitScripts")) {
            // usePluginsInInitScripts asserts using https://repo.gradle.org/gradle/repo
            return sample;
        }
        List<Command> commands = sample.getCommands();
        List<Command> modifiedCommands = new ArrayList<Command>();
        for (Command command : commands) {
            if ("gradle".equals(command.getExecutable())) {
                List<String> args = new ArrayList<String>(command.getArgs());
                args.add("--init-script");
                args.add(initScript.getAbsolutePath());
                args.add("-D" + PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY + "=" + gradlePluginRepositoryMirrorUrl());
                modifiedCommands.add(command.toBuilder().setArgs(args).build());
            } else {
                modifiedCommands.add(command);
            }
        }
        return new Sample(sample.getId(), sample.getProjectDir(), modifiedCommands);
    }
}
