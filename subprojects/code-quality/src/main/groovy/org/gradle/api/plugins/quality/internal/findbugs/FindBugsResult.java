/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.quality.internal.findbugs;

import java.io.Serializable;

public class FindBugsResult implements Serializable {

    private final int bugCount;
    private final int missingClassCount;
    private final int errorCount;
    private final Exception exception;

    public FindBugsResult(int bugCount, int missingClassCount, int errorCount) {
        this(bugCount, missingClassCount, errorCount, null);
    }

    public FindBugsResult(int bugCount, int missingClassCount, int errorCount, Exception exception) {
        this.bugCount = bugCount;
        this.missingClassCount = missingClassCount;
        this.errorCount = errorCount;
        this.exception = exception;
    }

    public int getBugCount() {
        return bugCount;
    }

    public int getMissingClassCount() {
        return missingClassCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public Exception getException() {
        return exception;
    }
}
