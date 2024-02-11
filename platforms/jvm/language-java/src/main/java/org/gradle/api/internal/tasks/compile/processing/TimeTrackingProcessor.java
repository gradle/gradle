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

package org.gradle.api.internal.tasks.compile.processing;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorResult;
import org.gradle.internal.Factory;

import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TimeTrackingProcessor extends DelegatingProcessor {

    private final AnnotationProcessorResult result;
    private final Stopwatch stopwatch;

    public TimeTrackingProcessor(Processor delegate, AnnotationProcessorResult result) {
        this(delegate, result, Ticker.systemTicker());
    }

    @VisibleForTesting
    protected TimeTrackingProcessor(Processor delegate, AnnotationProcessorResult result, Ticker ticker) {
        super(delegate);
        this.result = result;
        this.stopwatch = Stopwatch.createUnstarted(ticker);
    }

    @Override
    public Set<String> getSupportedOptions() {
        return track(new Factory<Set<String>>() {
            @Override
            public Set<String> create() {
                return TimeTrackingProcessor.super.getSupportedOptions();
            }
        });
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return track(new Factory<Set<String>>() {
            @Override
            public Set<String> create() {
                return TimeTrackingProcessor.super.getSupportedAnnotationTypes();
            }
        });
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return track(new Factory<SourceVersion>() {
            @Override
            public SourceVersion create() {
                return TimeTrackingProcessor.super.getSupportedSourceVersion();
            }
        });
    }

    @Override
    public void init(final ProcessingEnvironment processingEnv) {
        track(new Factory<Void>() {
            @Override
            public Void create() {
                TimeTrackingProcessor.super.init(processingEnv);
                return null;
            }
        });
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        return track(new Factory<Boolean>() {
            @Override
            public Boolean create() {
                return TimeTrackingProcessor.super.process(annotations, roundEnv);
            }
        });
    }

    @Override
    public Iterable<? extends Completion> getCompletions(final Element element, final AnnotationMirror annotation, final ExecutableElement member, final String userText) {
        return track(new Factory<Iterable<? extends Completion>>() {
            @Override
            public Iterable<? extends Completion> create() {
                return TimeTrackingProcessor.super.getCompletions(element, annotation, member, userText);
            }
        });
    }

    private <T> T track(Factory<T> factory) {
        stopwatch.start();
        try {
            return factory.create();
        } finally {
            stopwatch.stop();
            result.setExecutionTimeInMillis(stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }
}
