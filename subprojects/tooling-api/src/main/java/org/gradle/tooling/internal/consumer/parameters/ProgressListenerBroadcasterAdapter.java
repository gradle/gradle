/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.internal.consumer.parameters;

import org.gradle.api.NonNullApi;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.tooling.Failure;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.internal.consumer.DefaultFailure;
import org.gradle.tooling.internal.consumer.DefaultFileComparisonTestAssertionFailure;
import org.gradle.tooling.internal.consumer.DefaultTestAssertionFailure;
import org.gradle.tooling.internal.consumer.DefaultTestFrameworkFailure;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalFileComparisonTestAssertionFailure;
import org.gradle.tooling.internal.protocol.InternalTestAssertionFailure;
import org.gradle.tooling.internal.protocol.InternalTestFrameworkFailure;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@NonNullApi
public class ProgressListenerBroadcasterAdapter {
    private ListenerBroadcast<ProgressListener> listeners = new ListenerBroadcast<>(ProgressListener.class);
    private final Class<?> handledEventType;
    private final Class<?> handleProgressEventType;
    private final @Nullable Class<?> descriptorClass;

    public ProgressListenerBroadcasterAdapter(Class<?> handledEventType, Class<?> handleProgressEventType, @Nullable Class<?> descriptorClass, List<ProgressListener> listener) {
        this.handledEventType = handledEventType;
        this.handleProgressEventType = handleProgressEventType;
        this.descriptorClass = descriptorClass;
        this.listeners.addAll(listener);
    }

    public static List<Failure> toFailures(List<? extends InternalFailure> causes) {
        if (causes == null) {
            return null;
        }
        List<Failure> failures = new ArrayList<>();
        for (InternalFailure cause : causes) {
            failures.add(toFailure(cause));
        }
        return failures;
    }

    private static Failure toFailure(InternalFailure origFailure) {
        if (origFailure instanceof InternalTestAssertionFailure) {
            if (origFailure instanceof InternalFileComparisonTestAssertionFailure) {
                InternalTestAssertionFailure assertionFailure = (InternalTestAssertionFailure) origFailure;
                return new DefaultFileComparisonTestAssertionFailure(assertionFailure.getMessage(),
                    assertionFailure.getDescription(),
                    assertionFailure.getExpected(),
                    assertionFailure.getActual(),
                    toFailures(origFailure.getCauses()),
                    ((InternalTestAssertionFailure) origFailure).getClassName(),
                    ((InternalTestAssertionFailure) origFailure).getStacktrace(),
                    ((InternalFileComparisonTestAssertionFailure) origFailure).getExpectedContent(),
                    ((InternalFileComparisonTestAssertionFailure) origFailure).getActualContent()
                );
            }
            InternalTestAssertionFailure assertionFailure = (InternalTestAssertionFailure) origFailure;
            return new DefaultTestAssertionFailure(
                assertionFailure.getMessage(),
                assertionFailure.getDescription(),
                assertionFailure.getExpected(),
                assertionFailure.getActual(),
                toFailures(origFailure.getCauses()),
                ((InternalTestAssertionFailure) origFailure).getClassName(),
                ((InternalTestAssertionFailure) origFailure).getStacktrace()
            );
        } else if (origFailure instanceof InternalTestFrameworkFailure) {
            InternalTestFrameworkFailure frameworkFailure = (InternalTestFrameworkFailure) origFailure;
            return new DefaultTestFrameworkFailure(
                frameworkFailure.getMessage(),
                frameworkFailure.getDescription(),
                toFailures(origFailure.getCauses()),
                ((InternalTestFrameworkFailure) origFailure).getClassName(),
                ((InternalTestFrameworkFailure) origFailure).getStacktrace()
            );
        }
        return origFailure == null ? null : new DefaultFailure(
            origFailure.getMessage(),
            origFailure.getDescription(),
            toFailures(origFailure.getCauses()));
    }

    public boolean canHandle(Class<?> eventType) {
        return handledEventType.isAssignableFrom(eventType);
    }

    public boolean canHandleProgressEvent(Class<?> progressEventType) {
        return handleProgressEventType.isAssignableFrom(progressEventType);
    }

    public ListenerBroadcast<ProgressListener> getListeners() {
        return listeners;
    }

    public boolean canHandleDescriptor(Class<?> aClass) {
        return descriptorClass != null && descriptorClass.isAssignableFrom(aClass);
    }

    public void broadCastInterProgressEvent(InternalProgressEvent progressEvent) {
//        listeners.getSource().onProgress(progressEvent);
    }

    public void broadCast(Object progressEvent) {

    }
}
