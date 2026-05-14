/*
 * Copyright 2024 the original author or authors.
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

package gradlebuild.basics;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.ExecOutput;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class BuildEnvironmentService implements BuildService<BuildEnvironmentService.Parameters> {

    public interface Parameters extends BuildServiceParameters {
        DirectoryProperty getRootProjectDir();

        DirectoryProperty getRootProjectBuildDir();
    }

    @Inject
    protected abstract ProviderFactory getProviders();

    public Provider<String> getGitCommitId() {
        return git("rev-parse", "HEAD");
    }

    public Provider<String> getGitBranch() {
        return git("rev-parse", "--abbrev-ref", "HEAD");
    }

    public Provider<String> getScriptTemplateCommitId() {
        return git("log", "-1", "--format=%H", "--", "platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt");
    }

    private Provider<String> git(String... args) {
        File projectDir = getParameters().getRootProjectDir().getAsFile().get();
        ExecOutput execOutput = getProviders().exec(spec -> {
            spec.setWorkingDir(projectDir);
            spec.setIgnoreExitValue(true);
            List<String> commandLine = new ArrayList<>();
            commandLine.add("git");
            commandLine.addAll(Arrays.asList(args));
            if (OperatingSystem.current().isWindows()) {
                List<String> wrapped = new ArrayList<>(Arrays.asList("cmd.exe", "/d", "/c"));
                wrapped.addAll(commandLine);
                commandLine = wrapped;
            }
            spec.commandLine(commandLine);
        });
        return execOutput.getResult().zip(execOutput.getStandardOutput().getAsText(), (result, outputText) ->
            result.getExitValue() == 0
                ? outputText.trim()
                : "<unknown>" // It's a source distribution, we don't know.
        );
    }
}
