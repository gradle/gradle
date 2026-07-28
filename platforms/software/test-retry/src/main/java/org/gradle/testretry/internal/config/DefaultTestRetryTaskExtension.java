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
package org.gradle.testretry.internal.config;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.testretry.TestRetryTaskExtension;

import javax.inject.Inject;

public class DefaultTestRetryTaskExtension implements TestRetryTaskExtension {

    private final Property<Boolean> failOnPassedAfterRetry;
    private final Property<Boolean> failOnSkippedAfterRetry;
    private final Property<Integer> maxRetries;
    private final Property<Integer> maxFailures;
    private final Filter filter;

    private final ClassRetryCriteria classRetryCriteria;

    @Inject
    public DefaultTestRetryTaskExtension(ObjectFactory objects) {
        this.failOnPassedAfterRetry = objects.property(Boolean.class);
        this.failOnSkippedAfterRetry = objects.property(Boolean.class);
        this.maxRetries = objects.property(Integer.class);
        this.maxFailures = objects.property(Integer.class);
        this.filter = new FilterImpl(objects);
        this.classRetryCriteria = new ClassRetryCriteriaImpl(objects);
    }

    public Property<Boolean> getFailOnPassedAfterRetry() {
        return failOnPassedAfterRetry;
    }

    public Property<Boolean> getFailOnSkippedAfterRetry() {
        return failOnSkippedAfterRetry;
    }

    public Property<Integer> getMaxRetries() {
        return maxRetries;
    }

    public Property<Integer> getMaxFailures() {
        return maxFailures;
    }

    @Override
    public void filter(Action<? super Filter> action) {
        action.execute(filter);
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

    @Override
    public ClassRetryCriteria getClassRetry() {
        return classRetryCriteria;
    }

    @Override
    public void classRetry(Action<? super ClassRetryCriteria> action) {
        action.execute(classRetryCriteria);
    }

    private static final class FilterImpl implements Filter {

        private final SetProperty<String> includeClasses;
        private final SetProperty<String> includeAnnotationClasses;
        private final SetProperty<String> excludeClasses;
        private final SetProperty<String> excludeAnnotationClasses;

        public FilterImpl(ObjectFactory objects) {
            this.includeClasses = objects.setProperty(String.class);
            this.includeAnnotationClasses = objects.setProperty(String.class);
            this.excludeClasses = objects.setProperty(String.class);
            this.excludeAnnotationClasses = objects.setProperty(String.class);
        }

        @Override
        public SetProperty<String> getIncludeClasses() {
            return includeClasses;
        }

        @Override
        public SetProperty<String> getIncludeAnnotationClasses() {
            return includeAnnotationClasses;
        }

        @Override
        public SetProperty<String> getExcludeClasses() {
            return excludeClasses;
        }

        @Override
        public SetProperty<String> getExcludeAnnotationClasses() {
            return excludeAnnotationClasses;
        }
    }

    private static final class ClassRetryCriteriaImpl implements ClassRetryCriteria {

        private final SetProperty<String> includeClasses;
        private final SetProperty<String> includeAnnotationClasses;

        public ClassRetryCriteriaImpl(ObjectFactory objects) {
            this.includeClasses = objects.setProperty(String.class);
            this.includeAnnotationClasses = objects.setProperty(String.class);
        }

        @Override
        public SetProperty<String> getIncludeClasses() {
            return includeClasses;
        }

        @Override
        public SetProperty<String> getIncludeAnnotationClasses() {
            return includeAnnotationClasses;
        }
    }

}
