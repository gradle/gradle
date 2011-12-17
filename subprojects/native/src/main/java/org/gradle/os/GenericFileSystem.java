/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.os;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

class GenericFileSystem implements FileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericFileSystem.class);

    final boolean caseSensitive;
    final boolean canCreateSymbolicLink;

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean canCreateSymbolicLink() {
        return canCreateSymbolicLink;
    }

    public void createSymbolicLink(File link, File target) throws IOException {
        int returnCode = doCreateSymbolicLink(link, target);
        if (returnCode != 0) {
            throw new IOException("Failed to create symbolic link " + link
                    + " pointing to " + target + ". Return code is: " + returnCode);
        }
    }

    public boolean tryCreateSymbolicLink(File link, File target) {
        return doCreateSymbolicLink(link, target) == 0;
    }

    private int doCreateSymbolicLink(File link, File target) {
        try {
            return PosixUtil.current().symlink(target.getPath(), link.getPath());
        } catch (UnsatisfiedLinkError e) {
            // Assume symlink() is not available
            return 1;
        }
    }

    GenericFileSystem() {
        String content = generateUniqueContent();
        File file = null;
        try {
            checkJavaIoTmpDirExists();
            file = createFile(content);
            caseSensitive = probeCaseSensitive(file, content);
            canCreateSymbolicLink = probeCanCreateSymbolicLink(file, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    private String generateUniqueContent() {
        return UUID.randomUUID().toString();
    }

    private File createFile(String content) throws IOException {
        File file = File.createTempFile("gradle_fs_probing", null, null);
        Files.write(content, file, Charsets.UTF_8);
        return file;
    }

    private boolean probeCaseSensitive(File file, String content) {
        try {
            File upperCased = new File(file.getPath().toUpperCase());
            return !hasContent(upperCased, content);
        } catch (IOException e) {
            // not fully accurate but a sensible fallback
            // see http://stackoverflow.com/questions/1288102/how-do-i-detect-whether-the-file-system-is-case-sensitive
            boolean result = !new File("foo").equals(new File("FOO"));
            LOGGER.info("Failed to determine if file system is case sensitive. Best guess is '{}'.", result);
            return result;
        }
    }

    private boolean probeCanCreateSymbolicLink(File file, String content) {
        File link = null;
        try {
            link = generateUniqueTempFileName();
            int returnCode = doCreateSymbolicLink(link, file);
            return returnCode == 0 && hasContent(link, content);
        } catch (IOException e) {
            LOGGER.info("Failed to determine if file system can create symbolic links. Assuming it can't.");
            return false;
        } finally {
            FileUtils.deleteQuietly(link);
        }
    }

    private boolean hasContent(File file, String content) throws IOException {
        return file.exists() && Files.readFirstLine(file, Charsets.UTF_8).equals(content);
    }

    private File generateUniqueTempFileName() throws IOException {
        return new File(System.getProperty("java.io.tmpdir"), "gradle_unique_file_name" + UUID.randomUUID().toString());
    }
    
    private void checkJavaIoTmpDirExists() throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        if (!dir.exists()) {
            throw new IOException("java.io.tmpdir is set to a directory that doesn't exist: " + dir);
        }
    }
}
