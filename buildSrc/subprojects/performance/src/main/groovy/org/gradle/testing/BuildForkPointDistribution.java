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

package org.gradle.testing;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;

@CacheableTask
public class BuildForkPointDistribution extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(BuildForkPointDistribution.class);

    private String forkPointCommitId;

    private String baselineVersion = "defaults";

    @Input
    @Optional
    public String getForkPointCommitId() {
        return forkPointCommitId;
    }

    public void setForkPointCommitId(String forkPointCommitId) {
        this.forkPointCommitId = forkPointCommitId;
    }

    @OutputDirectory
    public File getForkPointDistributionDir() {
        return new File(getProject().getRootProject().getBuildDir(), "distributions/gradle-forkpoint");
    }

    @Internal
    public String getBaselineVersion() {
        return baselineVersion;
    }

    @TaskAction
    void buildDistribution() {
        LOGGER.quiet("Building fork point distribution for: " + forkPointCommitId);
        try {
            prepareGradleRepository();
            tryBuildDistribution(forkPointCommitId);
            setBaselineVersion();
            LOGGER.quiet("Building commit " + forkPointCommitId + " succeeded, now the baseline is " + baselineVersion);
        } catch (Exception e) {
            LOGGER.quiet("Building commit " + forkPointCommitId + " failed, fallback to default baseline.", e);
        }
    }

    private void setBaselineVersion() {
        baselineVersion = Stream.of(getForkPointDistributionDir().listFiles())
            .filter(file -> file.getName().endsWith("-bin.zip"))
            // gradle-5.0-commit-2149a1d-bin.zip -> 5.0-commit-2149a1d
            .map(file -> file.getName().substring("gradle-".length(), file.getName().length() - "-bin.zip".length())).findFirst()
            .get();
    }

    private void prepareGradleRepository() throws IOException {
        File cloneDir = getGradleCloneTmpDir();
        if (new File(cloneDir, ".git").isDirectory()) {
            exec(cloneDir, "git", "reset", "--hard");
            exec(cloneDir, "git", "fetch");
        } else {
            FileUtils.forceMkdir(cloneDir);
            exec(cloneDir.getParentFile(), "git", "clone", getProject().getRootDir().getAbsolutePath(), getGradleCloneTmpDir().getAbsolutePath(), "--no-checkout");
        }
    }

    private void exec(File workingDir, Object... command) {
        getProject().exec(spec -> {
            spec.commandLine(command);
            spec.workingDir(workingDir);
        });
    }

    private File getGradleCloneTmpDir() {
        return new File(getProject().getRootProject().getBuildDir(), "tmp/gradle-find-forkpoint");
    }


    private void tryBuildDistribution(String commit) throws Exception {
        exec(getGradleCloneTmpDir(), "git", "checkout", commit, "--force");
        exec(getGradleCloneTmpDir(), "./gradlew", ":distributions:binZip", ":toolingApi:toolingApiShadedJar");

        FileUtils.cleanDirectory(getForkPointDistributionDir());
        String baseVersion = new String(Files.readAllBytes(getGradleCloneTmpDir().toPath().resolve("version.txt"))).trim();

        copyToDistributionDir("build/distributions/gradle-" + baseVersion + "-bin.zip", "gradle-" + baseVersion + "-commit-" + commit + "-bin.zip");
        copyToDistributionDir("subprojects/tooling-api/build/shaded-jar/gradle-tooling-api-shaded-" + baseVersion + ".jar", "gradle-tooling-api-" + baseVersion + "-commit-" + commit + ".jar");
    }

    private void copyToDistributionDir(String srcRelativePath, String destRelativePath) throws IOException {
        Files.copy(getGradleCloneTmpDir().toPath().resolve(srcRelativePath), getForkPointDistributionDir().toPath().resolve(destRelativePath));
    }
}
