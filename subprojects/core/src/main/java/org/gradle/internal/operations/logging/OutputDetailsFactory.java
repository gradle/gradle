/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.operations.logging;

import org.gradle.internal.logging.buildoperation.LogEventBuildOperationProgressDetails;
import org.gradle.internal.logging.buildoperation.ProgressStartBuildOperationProgressDetails;
import org.gradle.internal.logging.buildoperation.StyledTextBuildOperationProgressDetails;
import org.gradle.internal.logging.events.CategorisedOutputEvent;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;

import java.util.List;

public final class OutputDetailsFactory {
    public static Object from(CategorisedOutputEvent event) {
        if (event instanceof LogEvent) {
            final LogEvent logEvent = (LogEvent) event;
            return new LogEventBuildOperationProgressDetails() {
                @Override
                public String getMessage() {
                    return logEvent.getMessage();
                }

                @Override
                public String getCategory() {
                    return logEvent.getCategory();
                }

                /**
                 * keep log level enum here?
                 * */
                @Override
                public int getLogLevel() {
                    return logEvent.getLogLevel().ordinal();
                }

                @Override
                public Throwable getThrowable() {
                    return logEvent.getThrowable();
                }
            };

        } else if (event instanceof StyledTextOutputEvent) {
            final StyledTextOutputEvent styledTextOutputEvent = (StyledTextOutputEvent) event;

            return new StyledTextBuildOperationProgressDetails() {
                @Override
                public String getCategory() {
                    return styledTextOutputEvent.getCategory();
                }

                @Override
                public int getLogLevel() {
                    return styledTextOutputEvent.getLogLevel().ordinal();
                }

                @Override
                public List<StyledTextOutputEvent.Span> getSpans() {
                    return styledTextOutputEvent.getSpans();
                }
            };
        } else if (event instanceof ProgressStartEvent) {
            final ProgressStartEvent progressStartEvent = (ProgressStartEvent) event;
            if (progressStartEvent.getLoggingHeader() != null || expectedFromBuildScanPlugin(progressStartEvent)) {
                return new ProgressStartBuildOperationProgressDetails() {
                    @Override
                    public String getMessage() {
                        return progressStartEvent.getDescription();
                    }

                    @Override
                    public String getCategory() {
                        return progressStartEvent.getCategory();
                    }

                    /**
                     * keep log level enum here?
                     * */
                    @Override
                    public int getLogLevel() {
                        return progressStartEvent.getLogLevel().ordinal();
                    }

                    @Override
                    public Throwable getThrowable() {
                        return null;
                    }
                };
            }

        }
        return null;
    }

    /**
     * workaround to ensure Download / Upload console log can be captured.
     *
     * Problem to workaround:
     *
     * When Download operation is triggered we generate 2 progress events:
     *  1. from the build operation executer including a referenced build operation but no logging header
     *  2. from `AbstractProgressLoggingHandler` that includes a logging header but no referenced build operation
     *
     *  By default only progress events with associated build operations are forwarded to the
     *  build operation listener.
     *
     *  The build scan plugin by default only handles progress starte events _with_ a logging header.
     *
     *  This workaround bypasses the 2nd limitations.
     * */
    private static boolean expectedFromBuildScanPlugin(ProgressStartEvent progressStartEvent) {
        return progressStartEvent.getDescription().startsWith("Download")
            || progressStartEvent.getDescription().startsWith("Upload");

    }

    private OutputDetailsFactory() {
    }
}
