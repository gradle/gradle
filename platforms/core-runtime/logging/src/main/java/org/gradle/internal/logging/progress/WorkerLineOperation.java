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

package org.gradle.internal.logging.progress;

import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.util.Path;

/**
 * A build operation for work that is shown on a worker line, with the line text as progress display name.
 * For a non-root build, the path of the build is added to the display name and put before the line text.
 */
public final class WorkerLineOperation implements RunnableBuildOperation {

    private final String displayName;
    private final String lineText;
    private final Runnable work;

    public WorkerLineOperation(Path buildPath, String displayName, String lineText, Runnable work) {
        this.displayName = Path.ROOT.equals(buildPath) ? displayName : displayName + " (" + buildPath.asString() + ")";
        this.lineText = lineText(buildPath, lineText);
        this.work = work;
    }

    /**
     * The line text of work of the given build: the text alone for the root build, or after the path of the build for a non-root build.
     */
    public static String lineText(Path buildPath, String text) {
        return Path.ROOT.equals(buildPath) ? text : buildPath.asString() + " > " + text;
    }

    @Override
    public void run(BuildOperationContext context) {
        work.run();
    }

    @Override
    public BuildOperationDescriptor.Builder description() {
        return BuildOperationDescriptor.displayName(displayName).progressDisplayName(lineText);
    }
}
