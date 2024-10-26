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
package org.gradle.internal.logging

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.console.StyledTextOutputBackedRenderer
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.time.Timestamp
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

import static org.gradle.internal.time.TestTime.timestampOf

abstract class OutputSpecification extends Specification {

    public static final String CATEGORY = 'category'
    private Long counter = 1

    protected String toNative(String value) {
        return TextUtil.toPlatformLineSeparators(value)
    }

    /**
     * Returns timestamp representing 10AM today in local time.
     */
    Timestamp getTenAm() {
        return timestampOf(getTenAmAsDate().getTimeInMillis())
    }

    String getTenAmFormatted() {
        return getTenAmAsDate().format(StyledTextOutputBackedRenderer.ISO_8601_DATE_TIME_FORMAT)
    }

    private GregorianCalendar getTenAmAsDate() {
        new GregorianCalendar(2012, Calendar.JUNE, 11, 10, 0, 0)
    }

    LogEvent event(String text) {
        return new LogEvent(tenAm, 'category', LogLevel.INFO, text, null)
    }

    LogEvent event(String text, LogLevel logLevel) {
        return new LogEvent(tenAm, 'category', logLevel, text, null)
    }

    LogEvent event(String text, LogLevel logLevel, long buildOperationId) {
        event(text, logLevel, new OperationIdentifier(buildOperationId))
    }

    LogEvent event(String text, LogLevel logLevel, OperationIdentifier buildOperationId) {
        return new LogEvent(tenAm, 'category', logLevel, text, null, buildOperationId)
    }

    LogEvent event(Timestamp timestamp, String text, LogLevel logLevel) {
        return new LogEvent(timestamp, 'category', logLevel, text, null)
    }

    LogEvent event(Timestamp timestamp, String text, LogLevel logLevel, OperationIdentifier buildOperationId) {
        return new LogEvent(timestamp, 'category', logLevel, text, null, buildOperationId)
    }

    LogEvent event(Timestamp timestamp, String text) {
        return new LogEvent(timestamp, 'category', LogLevel.INFO, text, null)
    }

    LogEvent event(String text, Throwable throwable) {
        return new LogEvent(tenAm, 'category', LogLevel.INFO, text, throwable)
    }

    ProgressStartEvent start(String description) {
        start(description: description)
    }

    ProgressStartEvent start(Long id) {
        start(id: id)
    }

    ProgressStartEvent start(Long id, String status) {
        start(id: id, status: status)
    }

    ProgressStartEvent start(Map args) {
        Long parentId = args.containsKey("parentId") ? args.parentId : null
        OperationIdentifier parent = parentId ? new OperationIdentifier(parentId) : null
        Object buildOperationId = args.containsKey("buildOperationId") ? new OperationIdentifier(args.buildOperationId) : null
        boolean buildOperationStart = args.buildOperationStart
        BuildOperationCategory buildOperationCategory = args.containsKey("buildOperationCategory") ? args.buildOperationCategory : BuildOperationCategory.UNCATEGORIZED
        Long id = args.containsKey("id") ? args.id : ++counter
        String category = args.containsKey("category") ? args.category : CATEGORY
        return new ProgressStartEvent(new OperationIdentifier(id), parent, tenAm, category, args.description, args.loggingHeader, args.status, 0, buildOperationStart, buildOperationId, buildOperationCategory)
    }

    ProgressEvent progress(String status) {
        long id = counter
        return new ProgressEvent(new OperationIdentifier(id), status, false)
    }

    ProgressCompleteEvent complete(String status) {
        long id = counter--
        return new ProgressCompleteEvent(new OperationIdentifier(id), tenAm, status, false)
    }

    ProgressCompleteEvent complete(Long id, status = 'STATUS') {
        new ProgressCompleteEvent(new OperationIdentifier(id), tenAm, status, false)
    }

    UpdateNowEvent updateNow() {
        new UpdateNowEvent(tenAm)
    }
}
