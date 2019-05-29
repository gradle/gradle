/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.util;

import junit.framework.ComparisonFailure;
import org.spockframework.runtime.ConditionFailedWithExceptionError;
import org.spockframework.runtime.WrongExceptionThrownError;
import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension;
import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;
import org.spockframework.runtime.model.FeatureInfo;

public class FailsWithMessageExtension extends AbstractAnnotationDrivenExtension<FailsWithMessage> {
    @Override
    public void visitFeatureAnnotation(FailsWithMessage annotation, FeatureInfo feature) {
        feature.getFeatureMethod().addInterceptor(new FailsWithMessageInterceptor(annotation));
    }

    private class FailsWithMessageInterceptor implements IMethodInterceptor {
        private final FailsWithMessage annotation;

        public FailsWithMessageInterceptor(FailsWithMessage annotation) {
            this.annotation = annotation;
        }

        @Override
        public void intercept(IMethodInvocation invocation) throws Throwable {
            try {
                invocation.proceed();
                throw new WrongExceptionThrownError(annotation.type(), null);
            } catch (ConditionFailedWithExceptionError error) {
                handleFailure(error.getCause());
            } catch (Throwable t) {
                handleFailure(t);
            }
        }

        private void handleFailure(Throwable t) {
            if (!annotation.type().isInstance(t)) {
                throw new WrongExceptionThrownError(annotation.type(), t);
            }
            if (!annotation.message().equals(t.getMessage())) {
                throw new ComparisonFailure("Unexpected message for exception.", annotation.message(), t.getMessage());
            }
        }
    }
}
