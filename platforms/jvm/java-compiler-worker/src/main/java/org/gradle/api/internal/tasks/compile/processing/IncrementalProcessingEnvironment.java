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

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Locale;
import java.util.Map;

/**
 * A decorator for the {@link ProcessingEnvironment} provided by the Java compiler,
 * which allows us to intercept calls from annotation processors in order to validate
 * their behavior.
 */
class IncrementalProcessingEnvironment implements ProcessingEnvironment {
    private final ProcessingEnvironment delegate;
    private final IncrementalFiler filer;

    IncrementalProcessingEnvironment(ProcessingEnvironment delegate, IncrementalFiler filer) {
        this.delegate = delegate;
        this.filer = filer;
    }

    @Override
    public Map<String, String> getOptions() {
        return delegate.getOptions();
    }

    @Override
    public Messager getMessager() {
        return delegate.getMessager();
    }

    @Override
    public Filer getFiler() {
        return filer;
    }

    @Override
    public Elements getElementUtils() {
        return delegate.getElementUtils();
    }

    @Override
    public Types getTypeUtils() {
        return delegate.getTypeUtils();
    }

    @Override
    public SourceVersion getSourceVersion() {
        return delegate.getSourceVersion();
    }

    @Override
    public Locale getLocale() {
        return delegate.getLocale();
    }
}
