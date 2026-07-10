/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.nativeintegration.filesystem.jdk7;

import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.internal.nativeintegration.filesystem.Symlink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Jdk7Symlink implements Symlink {
    private static final Logger LOGGER = LoggerFactory.getLogger(Jdk7Symlink.class);

    private final boolean symlinkCreationSupported;

    public Jdk7Symlink(TemporaryFileProvider temporaryFileProvider) {
        this(doesSystemSupportSymlinks(temporaryFileProvider));
    }

    protected Jdk7Symlink(boolean symlinkCreationSupported) {
        this.symlinkCreationSupported = symlinkCreationSupported;
    }

    @Override
    public boolean isSymlinkCreationSupported() {
        return symlinkCreationSupported;
    }

    @Override
    public void symlink(File link, File target) throws Exception {
        link.getParentFile().mkdirs();
        Files.createSymbolicLink(link.toPath(), target.toPath());
    }

    @Override
    public boolean isSymlink(File suspect) {
        return Files.isSymbolicLink(suspect.toPath());
    }

    private static boolean doesSystemSupportSymlinks(TemporaryFileProvider temporaryFileProvider) {
        Path sourceFile = null;
        Path linkFile = null;
        try {
            sourceFile = temporaryFileProvider.createTemporaryFile("symlink", "test").toPath();
            linkFile = temporaryFileProvider.createTemporaryFile("symlink", "test_link").toPath();

            Files.delete(linkFile);
            Files.createSymbolicLink(linkFile, sourceFile);
            return true;
        } catch (InternalError e) {
            if (e.getMessage().contains("Should not get here")) {
                // probably facing JDK-8046686
                LOGGER.debug("Unable to create a symlink. Your system is hitting JDK bug id JDK-8046686. Symlink support disabled.", e);
            } else {
                LOGGER.debug("Unexpected internal error", e);
            }
            return false;
        } catch (IOException e) {
            return false;
        } catch (UnsupportedOperationException e) {
            return false;
        } finally {
            try {
                if (sourceFile != null) {
                    Files.deleteIfExists(sourceFile);
                }
                if (linkFile != null) {
                    Files.deleteIfExists(linkFile);
                }
            } catch (IOException e) {
                // We don't really need to handle this.
            }
        }
    }
}
