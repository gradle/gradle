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
import org.gradle.api.internal.changedetection.resources.zip.ZipTreeSnapshot;
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy;
import org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy;
import org.gradle.api.internal.changedetection.state.TreeSnapshot;
import org.gradle.internal.IoActions;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.util.DeprecationLogger;

import java.io.IOException;
import java.util.zip.ZipException;

public class ClasspathResourceSnapshotter implements ResourceSnapshotter {
    private final ResourceSnapshotter entrySnapshotter;
    private final StringInterner stringInterner;

    public ClasspathResourceSnapshotter(ResourceSnapshotter entrySnapshotter, StringInterner stringInterner) {
        this.stringInterner = stringInterner;
        this.entrySnapshotter = entrySnapshotter;
    }

    @Override
    public void snapshot(TreeSnapshot fileTreeSnapshot, SnapshotCollector collector) {
        SnapshottableResource root = fileTreeSnapshot.getRoot();
        if (root != null) {
            if (root.getType() == FileType.Missing) {
                return;
            }
            TreeSnapshot elements = (root.getType() == FileType.RegularFile) ? new ZipTreeSnapshot((SnapshottableReadableResource) root) : fileTreeSnapshot;
            snapshotElements(root, elements, collector);
        } else {
            throw new GradleException("Tree without root file on Classpath");
        }
    }

    private void snapshotElements(SnapshottableResource root, TreeSnapshot contents, SnapshotCollector collector) {
        try {
            SnapshotCollector entryCollector = new DefaultSnapshotCollector(TaskFilePropertySnapshotNormalizationStrategy.RELATIVE, TaskFilePropertyCompareStrategy.UNORDERED, stringInterner);
            entryCollector = collector.recordSubCollector(root, entryCollector);
            for (SnapshottableResource resource : contents.getDescendants()) {
                entrySnapshotter.snapshot(resource, entryCollector);
            }
        } catch (ZipException e) {
            // ZipExceptions point to a problem with the Zip, we try to be lenient for now.
            hashMalformedZip(root, collector);
        } catch (IOException e) {
            // IOExceptions other than ZipException are failures.
            throw new UncheckedIOException("Error snapshotting jar [" + root.getName() + "]", e);
        } catch (Exception e) {
            // Other Exceptions can be thrown by invalid zips, too. See https://github.com/gradle/gradle/issues/1581.
            hashMalformedZip(root, collector);
        } finally {
            IoActions.closeQuietly(contents);
        }
    }

    private void hashMalformedZip(SnapshottableResource zipFile, SnapshotCollector collector) {
        DeprecationLogger.nagUserWith("Malformed jar [" + zipFile.getName() + "] found on classpath. Gradle 5.0 will no longer allow malformed jars on a classpath.");
        collector.recordSnapshot(zipFile, zipFile.getContent().getContentMd5());
    }
}
