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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.changedetection.resources.recorders.SnapshottingResultRecorder;
import org.gradle.api.internal.changedetection.resources.zip.SnapshottableZipTree;
import org.gradle.api.internal.changedetection.state.SnapshottableResourceTree;
import org.gradle.internal.Factory;
import org.gradle.internal.IoActions;
import org.gradle.util.DeprecationLogger;

import java.io.IOException;
import java.util.zip.ZipException;

public class ClasspathResourceSnapshotter extends AbstractSnapshotter {
    private final ResourceSnapshotter entrySnapshotter;
    private final Factory<SnapshottingResultRecorder> entryRecorderFactory;

    public ClasspathResourceSnapshotter(ResourceSnapshotter entrySnapshotter, Factory<SnapshottingResultRecorder> entryRecorderFactory) {
        this.entrySnapshotter = entrySnapshotter;
        this.entryRecorderFactory = entryRecorderFactory;
    }

    @Override
    protected void snapshotResource(SnapshottableResource resource, SnapshottingResultRecorder recorder) {
        if (resource instanceof SnapshottableReadableResource) {
            SnapshottableZipTree zipTree = new SnapshottableZipTree((SnapshottableReadableResource) resource);
            try {
                snapshotTree(zipTree, recorder);
            } finally {
                IoActions.closeQuietly(zipTree);
            }
        }
    }

    @Override
    protected void snapshotTree(SnapshottableResourceTree tree, SnapshottingResultRecorder recorder) {
        try {
            SnapshottingResultRecorder entryRecorder = entryRecorderFactory.create();
            if (!(tree instanceof SnapshottableZipTree)) {
                entryRecorder = recorder.recordCompositeResult(tree.getRoot(), entryRecorder);
            }
            for (SnapshottableResource resource : tree.getDescendants()) {
                entrySnapshotter.snapshot(resource, entryRecorder);
            }
            if (tree instanceof SnapshottableZipTree) {
                recorder.recordResult(tree.getRoot(), entryRecorder.getHash(null));
            }
        } catch (ZipException e) {
            // ZipExceptions point to a problem with the Zip, we try to be lenient for now.
            hashMalformedZip(tree.getRoot(), recorder);
        } catch (IOException e) {
            // IOExceptions other than ZipException are failures.
            throw new UncheckedIOException("Error snapshotting jar [" + tree.getRoot().getName() + "]", e);
        } catch (UncheckedIOException e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof ZipException)) {
                throw e;
            }
            hashMalformedZip(tree.getRoot(), recorder);
        } catch (Exception e) {
            // Other Exceptions can be thrown by invalid zips, too. See https://github.com/gradle/gradle/issues/1581.
            hashMalformedZip(tree.getRoot(), recorder);
        }
    }

    private void hashMalformedZip(SnapshottableResource zipFile, SnapshottingResultRecorder recorder) {
        DeprecationLogger.nagUserWith("Malformed jar [" + zipFile.getName() + "] found on classpath. Gradle 5.0 will no longer allow malformed jars on a classpath.");
        recorder.recordResult(zipFile, zipFile.getContent().getContentMd5());
    }
}
