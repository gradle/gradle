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

import org.gradle.internal.nativeintegration.filesystem.Symlink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Jdk7Symlink implements Symlink {
    private static final Logger LOGGER = LoggerFactory.getLogger(Jdk7Symlink.class);

    private final boolean symlinksSupported;

    public Jdk7Symlink() {
        this(doesSystemSupportSymlinks());
    }

    protected Jdk7Symlink(boolean symlinksSupported) {
        this.symlinksSupported = symlinksSupported;
    }

    @Override
    public boolean isSymlinkSupported() {
        return symlinksSupported;
    }

    @Override
    public void symlink(File link, File target) throws Exception {
        link.getParentFile().mkdirs();
        Files.createSymbolicLink(link.toPath(), target.toPath());
    }

    @Override
    public boolean isSymlink(File suspect) {
        if (isSymlinkSupported()) {
            return Files.isSymbolicLink(suspect.toPath());
        }
        return false;
    }

    private static boolean doesSystemSupportSymlinks() {
        Path sourceFile = null;
        Path linkFile = null;
        try {
            sourceFile = Files.createTempFile("symlink", "test");
            linkFile = Files.createTempFile("symlink", "test_link");

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
                if (sourceFile != null && sourceFile.toFile().exists()) {
                    Files.delete(sourceFile);
                }
                if (linkFile != null && linkFile.toFile().exists()) {
                    Files.delete(linkFile);
                }
            } catch (IOException e) {
                // We don't really need to handle this.
            }
        }
    }
}
