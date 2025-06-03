/*
 * Copyright 2025 the original author or authors.
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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.gradle.test.fixtures.file.TestFile;

import java.io.IOException;

public class GitUtility {
    /**
     * Some integration tests need to run git commands in test directory,
     * but distributed-test-remote-executor has no .git directory so we init a "dummy .git dir".
     */
    public static void initGitDir(TestFile testDirectory) throws GitAPIException, IOException, ConfigInvalidException {
        SystemReader.setInstance(new SystemReader.Delegate(SystemReader.getInstance()) {
            private FileBasedConfig emptyConfig(Config parent, FS fs) {
                return new FileBasedConfig(parent, null, fs) {
                    @Override
                    public void load() {
                        // load an empty config
                    }

                    @Override
                    public boolean isOutdated() {
                        return false;
                    }

                    @Override
                    public void save() throws IOException {
                        // do not try to write anything
                    }
                };
            }

            @Override
            public StoredConfig getUserConfig() throws ConfigInvalidException, IOException {
                return emptyConfig(super.getUserConfig(), FS.DETECTED);
            }
        });

        try (Git git = Git.init().setDirectory(testDirectory).call()) {
            testDirectory.file("initial-commit").createNewFile();
            git.add().addFilepattern("initial-commit").call();
            git.commit().setMessage("Initial commit").call();
        }
        RepositoryCache.clear();  // https://github.com/eclipse-jgit/jgit/issues/155#issuecomment-2765437816
    }
}
