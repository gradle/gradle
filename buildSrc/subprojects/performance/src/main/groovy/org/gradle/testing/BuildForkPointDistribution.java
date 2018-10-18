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

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;

import static org.apache.commons.io.FileUtils.cleanDirectory;
import static org.apache.commons.io.FileUtils.forceMkdir;

@CacheableTask
public class BuildForkPointDistribution extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(BuildForkPointDistribution.class);

    private final DirectoryProperty forkPointDistributionDir = getProject().getObjects().directoryProperty();

    private String forkPointCommitId;

    private String baselineVersion = "defaults";

    private boolean ciServer;

    @Inject
    public BuildForkPointDistribution() {
        forkPointDistributionDir.set(getProject().getRootProject().getLayout().getBuildDirectory().dir("distributions/gradle-forkpoint"));
    }

    @Input
    @Optional
    public String getForkPointCommitId() {
        return forkPointCommitId;
    }

    public void setForkPointCommitId(String forkPointCommitId) {
        this.forkPointCommitId = forkPointCommitId;
    }

    @OutputDirectory
    public DirectoryProperty getForkPointDistributionDir() {
        return forkPointDistributionDir;
    }

    @Internal
    public String getBaselineVersion() {
        return baselineVersion;
    }

    public void setCiServer(boolean ciServer) {
        this.ciServer = ciServer;
    }

    @TaskAction
    void buildDistribution() {
        LOGGER.quiet("Building fork point distribution for: " + forkPointCommitId);
        try {
            prepareGradleRepository();
            tryBuildDistribution(forkPointCommitId);
            LOGGER.quiet("Building commit " + forkPointCommitId + " succeeded, now the baseline is " + baselineVersion);
        } catch (Exception e) {
            LOGGER.quiet("Building commit " + forkPointCommitId + " failed, fallback to default baseline.", e);
        }
    }

    private void setBaselineVersion(String baseVersion) {
        baselineVersion = baseVersion + "-commit-" + forkPointCommitId;
    }

    private void prepareGradleRepository() throws IOException {
        File cloneDir = getGradleCloneTmpDir();
        if (new File(cloneDir, ".git").isDirectory()) {
            exec(cloneDir, "git", "reset", "--hard");
            exec(cloneDir, "git", "fetch");
        } else {
            forceMkdir(cloneDir);
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
        cleanDirectory(getForkPointDistributionDir().getAsFile().get());

        String baseVersion = new String(Files.readAllBytes(getGradleCloneTmpDir().toPath().resolve("version.txt"))).trim();

        exec(getGradleCloneTmpDir(), (Object[]) getBuildCommands(commit, baseVersion));
        setBaselineVersion(baseVersion);
    }

    private Object[] getBuildCommands(String commit, String baseVersion) {
        String[] commands = new String[]{
            "./gradlew",
            "install",
            "-Pgradle_installPath=" + getDestinationFile("gradle-" + baseVersion + "-commit-" + commit),
            ":toolingApi:installToolingApiShadedJar",
            "-PtoolingApiShadedJarInstallPath=" + getDestinationFile("gradle-tooling-api-" + baseVersion + "-commit-" + commit + ".jar")
        };

        if (ciServer) {
            String[] buildScanParams = new String[]{"--init-script", new File(getGradleCloneTmpDir(), "gradle/init-scripts/build-scan.init.gradle.kts").getAbsolutePath()};
            return Stream.of(commands, buildScanParams).flatMap(Stream::of).toArray();
        } else {
            return commands;
        }
    }

    private String getDestinationFile(String fileName) {
        return new File(getForkPointDistributionDir().getAsFile().get(), fileName).getAbsolutePath();
    }
}
