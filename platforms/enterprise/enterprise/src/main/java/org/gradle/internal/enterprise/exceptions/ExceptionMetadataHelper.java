/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.enterprise.exceptions;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.api.tasks.VerificationException;
import org.gradle.groovy.scripts.ScriptCompilationException;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.exceptions.MultiCauseException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExceptionMetadataHelper {

    private static final String METADATA_KEY_TASK_PATH = "taskPath";
    private static final String METADATA_KEY_SCRIPT_LINE_NUMBER = "scriptLineNumber";
    private static final String METADATA_KEY_SCRIPT_FILE = "scriptFile";
    private static final String METADATA_KEY_SOURCE_DISPLAY_NAME = "sourceDisplayName";
    private static final String METADATA_KEY_LOCATION = "location";
    private static final String METADATA_KEY_LINE_NUMBER = "lineNumber";
    private static final String METADATA_KEY_IS_MULTICAUSE = "isMultiCause";
    private static final String METADATA_KEY_IS_VERIFICATION_EXCEPTION = "isVerificationException";

    public static Map<String, String> getMetadata(Throwable t) {
        Map<String, String> metadata = new LinkedHashMap<>();

        if (t instanceof TaskExecutionException) {
            TaskExecutionException taskExecutionException = (TaskExecutionException) t;
            String taskPath = ((TaskInternal) taskExecutionException.getTask()).getIdentityPath().getPath();
            metadata.put(METADATA_KEY_TASK_PATH, taskPath);
        }

        if (t instanceof ScriptCompilationException) {
            ScriptCompilationException sce = (ScriptCompilationException) t;
            metadata.put(METADATA_KEY_SCRIPT_FILE, sce.getScriptSource().getFileName());
            Integer sceLineNumber = sce.getLineNumber();
            if (sceLineNumber != null) {
                metadata.put(METADATA_KEY_SCRIPT_LINE_NUMBER, sceLineNumber.toString());
            }
        }

        if (t instanceof LocationAwareException) {
            LocationAwareException lae = (LocationAwareException) t;
            metadata.put(METADATA_KEY_SOURCE_DISPLAY_NAME, lae.getSourceDisplayName());
            Integer laeLineNumber = lae.getLineNumber();
            if (laeLineNumber != null) {
                metadata.put(METADATA_KEY_LINE_NUMBER, laeLineNumber.toString());
            }
            metadata.put(METADATA_KEY_LOCATION, lae.getLocation());
        }

        if (t instanceof MultiCauseException) {
            metadata.put(METADATA_KEY_IS_MULTICAUSE, String.valueOf(true));
        }

        if (t instanceof VerificationException) {
            metadata.put(METADATA_KEY_IS_VERIFICATION_EXCEPTION, String.valueOf(true));
        }

        return metadata;
    }

    public static List<? extends Throwable> extractCauses(Throwable t) {
        if (t instanceof MultiCauseException) {
            MultiCauseException mce = (MultiCauseException) t;
            List<? extends Throwable> mceCauses = mce.getCauses();
            if (mceCauses != null && !mceCauses.isEmpty()) {
                return mceCauses;
            } else {
                return Collections.emptyList();
            }
        } else {
            Throwable cause = t.getCause();
            if (cause != null) {
                return Collections.singletonList(cause);
            } else {
                return Collections.emptyList();
            }
        }
    }

    private ExceptionMetadataHelper() {
    }

}
