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

package org.gradle.api.internal.changedetection.resources;

import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.resources.zip.ZipSnapshotTree;
import org.gradle.api.internal.changedetection.state.SnapshotTree;
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy;
import org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy;
import org.gradle.internal.Factory;
import org.gradle.internal.IoActions;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.util.DeprecationLogger;

import java.io.IOException;
import java.util.zip.ZipException;

public class ClasspathResourceSnapshotter extends AbstractResourceSnapshotter {
    private final Factory<ResourceSnapshotter> entrySnapshotterFactory;

    public ClasspathResourceSnapshotter(Factory<ResourceSnapshotter> entrySnapshotterFactory, StringInterner stringInterner) {
        super(TaskFilePropertySnapshotNormalizationStrategy.NONE, TaskFilePropertyCompareStrategy.ORDERED, stringInterner);
        this.entrySnapshotterFactory = entrySnapshotterFactory;
    }

    @Override
    public void snapshot(SnapshotTree fileTreeSnapshot) {
        SnapshottableResource root = fileTreeSnapshot.getRoot();
        if (root != null) {
            if (root.getType() == FileType.Missing) {
                return;
            }
            SnapshotTree elements = (root.getType() == FileType.RegularFile) ? new ZipSnapshotTree(root) : fileTreeSnapshot;
            snapshotElements(root, elements);
        } else {
            throw new GradleException("Tree without root file on Classpath");
        }
    }

    private void snapshotElements(SnapshottableResource root, SnapshotTree contents) {
        try {
            ResourceSnapshotter entrySnapshotter = entrySnapshotterFactory.create();
            recordSnapshotter(root, entrySnapshotter);
            for (SnapshottableResource resource : contents.getElements()) {
                entrySnapshotter.snapshot(resource);
            }
        } catch (ZipException e) {
            // ZipExceptions point to a problem with the Zip, we try to be lenient for now.
            hashMalformedZip(root);
        } catch (IOException e) {
            // IOExceptions other than ZipException are failures.
            throw new UncheckedIOException("Error snapshotting jar [" + root.getName() + "]", e);
        } catch (Exception e) {
            // Other Exceptions can be thrown by invalid zips, too. See https://github.com/gradle/gradle/issues/1581.
            hashMalformedZip(root);
        } finally {
            IoActions.closeQuietly(contents);
        }
    }

    private void hashMalformedZip(SnapshottableResource zipFile) {
        DeprecationLogger.nagUserWith("Malformed jar [" + zipFile.getName() + "] found on classpath. Gradle 5.0 will no longer allow malformed jars on a classpath.");
        recordSnapshot(zipFile, zipFile.getContent().getContentMd5());
    }
}
