/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.git.internal;

import com.google.common.collect.Sets;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.gradle.api.GradleException;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.VersionControlSystem;
import org.gradle.vcs.VersionRef;
import org.gradle.vcs.git.GitVersionControlSpec;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A Git {@link VersionControlSystem} implementation.
 */
public class GitVersionControlSystem implements VersionControlSystem {
    @Override
    public void populate(File workingDir, VersionRef ref, VersionControlSpec spec) {
        GitVersionControlSpec gitSpec = cast(spec);

        // TODO: Assuming the default branch for the repository
        File dbDir = new File(workingDir, ".git");
        if (dbDir.exists() && dbDir.isDirectory()) {
            updateRepo(workingDir, gitSpec);
        } else {
            cloneRepo(workingDir, gitSpec);
        }
    }

    public void populate(File workingDir, VersionControlSpec spec) {
        populate(workingDir, new DefaultVersionRef(), spec);
    }

    @Override
    public Set<VersionRef> getAvailableVersions(VersionControlSpec spec) {
        GitVersionControlSpec gitSpec = cast(spec);
        Set<VersionRef> versions = Sets.newHashSet();
        Collection<Ref> refs = Collections.emptyList();
        try {
            refs = Git.lsRemoteRepository().setRemote(safeRepositoryUrl(gitSpec.getUrl())).call();
        } catch (GitAPIException e) {
            throw wrapGitCommandException("ls-remote", gitSpec.getUrl(), null, e);
        }
        for (Ref ref : refs) {
            versions.add(GitVersionRef.from(ref));
        }
        return versions;
    }

    private static void cloneRepo(File workingDir, GitVersionControlSpec gitSpec) {
        CloneCommand clone = Git.cloneRepository().setURI(gitSpec.getUrl().toString()).setDirectory(workingDir);
        Git git = null;
        try {
            git = clone.call();
        } catch (GitAPIException e) {
            throw wrapGitCommandException("clone", gitSpec.getUrl(), workingDir, e);
        } catch (JGitInternalException e) {
            throw wrapGitCommandException("clone", gitSpec.getUrl(), workingDir, e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    private static void updateRepo(File workingDir, GitVersionControlSpec gitSpec) {
        Git git = null;
        try {
            git = Git.open(workingDir);
            git.pull().setRemote(getRemoteForUrl(git.getRepository(), gitSpec.getUrl())).call();
        } catch (IOException e) {
            throw wrapGitCommandException("update", gitSpec.getUrl(), workingDir, e);
        } catch (GitAPIException e) {
            throw wrapGitCommandException("update", gitSpec.getUrl(), workingDir, e);
        } catch (JGitInternalException e) {
            throw wrapGitCommandException("update", gitSpec.getUrl(), workingDir, e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    private static String getRemoteForUrl(Repository repository, URI url) {
        Config config = repository.getConfig();
        Set<String> remotes = config.getSubsections("remote");
        Set<String> foundUrls = new HashSet<String>();

        for (String remote : remotes) {
            String remoteUrl = config.getString("remote", remote, "url");
            if (remoteUrl.equals(safeRepositoryUrl(url))) {
                return remote;
            } else {
                foundUrls.add(remoteUrl);
            }
        }
        throw new GradleException(String.format("Could not find remote with url: %s. Found: %s", url, foundUrls));
    }

    // This is a horrible hack to work around a bug in how jgit stores and expects file urls for remotes.
    private static String safeRepositoryUrl(URI url) {
        String urlString = url.toString();
        if (urlString.startsWith("file:/") && !urlString.startsWith("file:///")) {
            return urlString.replace("file:/", "file:///");
        }
        return urlString;
    }

    private static GitVersionControlSpec cast(VersionControlSpec spec) {
        if (!(spec instanceof GitVersionControlSpec)) {
            throw new IllegalArgumentException("The GitVersionControlSystem can only handle GitVersionControlSpec instances.");
        }
        return (GitVersionControlSpec) spec;
    }

    private static GradleException wrapGitCommandException(String commandName, URI repoUrl, File workingDir, Exception e) {
        return new GradleException(String.format("Could not %s: %s from %s", commandName, repoUrl, workingDir), e);
    }
}
