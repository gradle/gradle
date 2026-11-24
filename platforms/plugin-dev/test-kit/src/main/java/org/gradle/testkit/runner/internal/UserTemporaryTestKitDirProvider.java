/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.testkit.runner.internal;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.file.nio.OwnerOnlyFiles;
import org.gradle.internal.file.nio.OwnerOnlyFiles.UntrustedDirectoryException;
import org.gradle.testkit.runner.InvalidRunnerConfigurationException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Provides the TestKit directory used when none is configured and the runner cannot use a location managed by the build.
 * <p>
 * The directory lives in a temporary directory that may be shared with other users of the machine.
 * It is therefore specific to the current user, only accessible to that user,
 * and an existing directory is only used when other users had no way to put content in it.
 */
@NullMarked
class UserTemporaryTestKitDirProvider implements TestKitDirProvider {

    private static final String DIR_NAME_PREFIX = ".gradle-test-kit-";
    private static final String UNKNOWN_USER = "unknown";

    private final File tmpDir;
    private final File dir;

    UserTemporaryTestKitDirProvider(File tmpDir, @Nullable String userName) {
        this.tmpDir = tmpDir;
        this.dir = new File(tmpDir, dirNameFor(userName));
    }

    @Override
    public File getDir() {
        return dir;
    }

    /**
     * Creates the directory when it does not exist, and verifies it can be trusted otherwise.
     */
    @Override
    public File ensureDirExists() {
        try {
            OwnerOnlyFiles.createOrVerifyPrivateDirectory(dir);
        } catch (UntrustedDirectoryException e) {
            throw refused(e.getReason(), e);
        } catch (IOException e) {
            throw failure("Unable to create " + dir.getAbsolutePath() + " as the TestKit directory.", "Check the permissions of " + tmpDir.getAbsolutePath(), e);
        }
        if (!dir.canWrite()) {
            throw refused("it is not writable", null);
        }
        return dir;
    }

    private InvalidRunnerConfigurationException refused(String reason, @Nullable IOException cause) {
        return failure("Unable to use " + dir.getAbsolutePath() + " as the TestKit directory: " + reason + ".", "Delete it", cause);
    }

    private static InvalidRunnerConfigurationException failure(String problem, String solution, @Nullable IOException cause) {
        InvalidRunnerConfigurationException failure = new InvalidRunnerConfigurationException(
            problem + " " + solution + ", or set another TestKit directory using GradleRunner.withTestKitDir() or the '" + DefaultGradleRunner.TEST_KIT_DIR_SYS_PROP + "' system property. "
                + new DocumentationRegistry().getDocumentationRecommendationFor("information", "test_kit", "sec:controlling_the_build_environment")
        );
        if (cause != null) {
            failure.initCause(cause);
        }
        return failure;
    }

    private static String dirNameFor(@Nullable String userName) {
        String sanitizedUserName = userName == null ? "" : userName.replaceAll("[^A-Za-z0-9._-]", "_");
        return DIR_NAME_PREFIX + (sanitizedUserName.isEmpty() ? UNKNOWN_USER : sanitizedUserName);
    }
}
