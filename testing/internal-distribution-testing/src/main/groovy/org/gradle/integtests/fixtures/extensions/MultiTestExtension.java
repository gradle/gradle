/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.extensions;

import org.spockframework.runtime.extension.IAnnotationDrivenExtension;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.SpecInfo;

import java.lang.annotation.Annotation;

public abstract class MultiTestExtension<T extends Annotation> implements IAnnotationDrivenExtension<T> {

    @Override
    public void visitSpecAnnotation(T annotation, SpecInfo spec) {
        if (!spec.getFeatures().isEmpty()) {
            AbstractMultiTestInterceptor interceptor = makeInterceptor(spec.getBottomSpec().getReflection());
            for (FeatureInfo feature : spec.getFeatures()) {
                interceptor.interceptFeature(feature);
            }
        }
    }

    protected abstract AbstractMultiTestInterceptor makeInterceptor(Class<?> testClass);
}
