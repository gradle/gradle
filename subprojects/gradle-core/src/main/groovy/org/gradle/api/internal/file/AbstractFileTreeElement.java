/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.Transformer;
import org.gradle.api.GradleException;
import org.apache.commons.io.IOUtils;

import java.io.*;

public abstract class AbstractFileTreeElement implements FileTreeElement {
    public abstract String getDisplayName();

    @Override
    public String toString() {
        return getDisplayName();
    }

    public boolean copyTo(File target) {
        try {
            if (!needsCopy(target)) {
                return false;
            }

            target.getParentFile().mkdirs();

            if (isDirectory()) {
                target.mkdirs();
            } else {
                copyFile(target);
            }
            target.setLastModified(getLastModified());
            return true;
        } catch (Exception e) {
            throw new GradleException(String.format("Could not copy %s to '%s'.", getDisplayName(), target));
        }
    }

    private void copyFile(File target) throws IOException {
        FileOutputStream outputStream = new FileOutputStream(target);
        InputStream inputStream = open();
        try {
            IOUtils.copyLarge(inputStream, outputStream);
        } finally {
            IOUtils.closeQuietly(outputStream);
            IOUtils.closeQuietly(inputStream);
        }
    }

    public boolean copyTo(File target, Transformer<Reader> readerFactory) {
        try {
            if (!needsCopy(target)) {
                return false;
            }
            target.getParentFile().mkdirs();

            Reader inReader = readerFactory.transform(new InputStreamReader(open()));
            FileWriter fWriter = new FileWriter(target);
            try {
                IOUtils.copyLarge(inReader, fWriter);
            } finally {
                IOUtils.closeQuietly(inReader);
                IOUtils.closeQuietly(fWriter);
            }
            target.setLastModified(getLastModified());
            return true;
        } catch (Exception e) {
            throw new GradleException(String.format("Could not copy %s to '%s'.", getDisplayName(), target));
        }
    }

    boolean needsCopy(File dest) {
        if (dest.exists()) {
            if (getLastModified() == dest.lastModified()) {
                return false;
            }
            // possibly add option to check file size too
        }
        return true;
    }
}
