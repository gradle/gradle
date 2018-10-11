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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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
import java.nio.file.Files;
import java.util.stream.Stream;

@CacheableTask
public class BuildForkPointDistribution extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(BuildForkPointDistribution.class);
    private static final int MAX_BACKTRACK_COMMIT_COUNT = 10;

    private String forkPointCommitId;

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
    public String getVersion() {
        return Stream.of(getForkPointDistributionDir().listFiles())
            .filter(file -> file.getName().endsWith("-bin.zip"))
            // gradle-5.0-commit-2149a1df4eb5f13b7b136c64dd31ce38c114474a-bin.zip -> 5.0-commit-2149a1df4eb5f13b7b136c64dd31ce38c114474a
            .map(file -> file.getName().substring("gradle-".length(), file.getName().length() - "-bin.zip".length())).findFirst()
            .get();
    }

    @TaskAction
    void buildDistribution() throws Exception {
        LOGGER.quiet("Building fork point distribution for: " + forkPointCommitId);

        Git git = prepareGradleRepository();

        try (RevWalk walk = new RevWalk(git.getRepository())) {
            RevCommit commit = walk.parseCommit(git.getRepository().resolve(forkPointCommitId));

            int retryCount = 0;
            while (true) {
                if (retryCount++ >= MAX_BACKTRACK_COMMIT_COUNT) {
                    throw new IllegalStateException("Can't find a buildable commit after retries.");
                }

                LOGGER.quiet("Try to build commit: " + commit.getId().getName());

                if (isBuildable(commit)) {
                    LOGGER.quiet("Building commit " + commit.getId().getName() + " succeeded.");
                    return;
                }
                // Always choose the first parent as it's on master
                commit = walk.parseCommit(commit.getParent(0).getId());
            }
        }
    }

    private Git prepareGradleRepository() throws Exception {
        if (new File(getGradleCloneTmpDir(), ".git").isDirectory()) {
            return performUpdate();
        } else {
            return performClone();
        }
    }

    private Git performClone() throws Exception {
        return Git.cloneRepository()
            .setURI("https://github.com/gradle/gradle.git")
            .setDirectory(getGradleCloneTmpDir())
            .call();
    }

    private Git performUpdate() throws Exception {
        Git git = Git.open(new File(getGradleCloneTmpDir(), ".git"));
        git.fetch().call();
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef("refs/remotes/origin/master").call();
        return git;
    }

    private void performCheckout(String commitId) throws Exception {
        Git.open(new File(getGradleCloneTmpDir(), ".git")).checkout().setForce(true).setName(commitId).call();
    }

    private File getGradleCloneTmpDir() {
        return new File(getProject().getRootProject().getBuildDir(), "tmp/gradle-find-forkpoint");
    }


    private boolean isBuildable(RevCommit commit) throws Exception {
        performCheckout(commit.getId().getName());

        Process process = new ProcessBuilder()
            .command(new File(getGradleCloneTmpDir(), "gradlew").getAbsolutePath(), ":distributions:binZip", ":toolingApi:toolingApiShadedJar")
            .directory(getGradleCloneTmpDir()).inheritIO().start();

        boolean success = process.waitFor() == 0;

        if (success) {
            FileUtils.cleanDirectory(getForkPointDistributionDir());
            String baseVersion = new String(Files.readAllBytes(getGradleCloneTmpDir().toPath().resolve("version.txt"))).trim();
            Files.copy(getGradleCloneTmpDir().toPath().resolve("build/distributions/gradle-" + baseVersion + "-bin.zip"),
                getForkPointDistributionDir().toPath().resolve("gradle-" + baseVersion + "-commit-" + commit.getId().getName() + "-bin.zip"));
            Files.copy(getGradleCloneTmpDir().toPath().resolve("subprojects/tooling-api/build/shaded-jar/gradle-tooling-api-shaded-" + baseVersion + ".jar"),
                getForkPointDistributionDir().toPath().resolve("gradle-tooling-api-" + baseVersion + "-commit-" + commit.getId().getName() + ".jar"));
        }

        return success;
    }
}
