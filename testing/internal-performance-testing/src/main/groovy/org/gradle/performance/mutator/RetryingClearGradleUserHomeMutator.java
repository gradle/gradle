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

package org.gradle.performance.mutator;

import org.gradle.profiler.mutations.AbstractScheduledMutator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Clone of {@link org.gradle.profiler.mutations.ClearGradleUserHomeMutator} that retries a few times before giving up.
 *
 * <p>
 * Useful with `--no-daemon` single-use daemon mode, where the daemon may not have released all files in the Gradle user home directory
 * after being stopped.
 * </p>
 *
 * <p>
 * This will become unnecessary once Gradle waits for the daemon to completely shut down before `--stop` exits.
 * </p>
 */
public class RetryingClearGradleUserHomeMutator extends AbstractScheduledMutator {
    private static final int MAX_RETRIES = 3;

    private final File gradleUserHome;

    public RetryingClearGradleUserHomeMutator(File gradleUserHome, Schedule schedule) {
        super(schedule);
        this.gradleUserHome = gradleUserHome;
    }

    @Override
    protected void executeOnSchedule() {
        System.out.println(String.format("> Cleaning Gradle user home: %s", gradleUserHome.getAbsolutePath()));
        if (!gradleUserHome.exists()) {
            throw new IllegalArgumentException(String.format(
                "Cannot delete Gradle user home directory (%s) since it does not exist",
                gradleUserHome
            ));
        }
        // Sometimes the build / daemon processes may not have released all files in the Gradle user home directory yet
        // so we may need to retry a few times before we can delete the directory
        int retry = 0;
        while (true) {
            try {
                deleteContents();
                return;
            } catch (IOException | IllegalStateException e) {
                if (retry == MAX_RETRIES) {
                    throw new RuntimeException(e);
                }
                retry++;
                System.out.println(String.format(
                    "Failed to delete contents of Gradle user home directory (%s), retrying (%s/%s)...",
                    gradleUserHome, retry, MAX_RETRIES
                ));
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interruptedException) {
                    RuntimeException ex = new RuntimeException("Interrupted while retrying", interruptedException);
                    ex.addSuppressed(e);
                    throw ex;
                }
            }
        }
    }

    private void deleteContents() throws IOException {
        try (Stream<Path> paths = Files.list(gradleUserHome.toPath())) {
            paths
                // Don't delete the wrapper dir, since this is where the Gradle distribution we are going to run is located
                .filter(path -> !path.getFileName().toString().equals("wrapper"))
                .forEach(path -> delete(path.toFile()));
        }
    }
}
