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

package org.gradle.performance.annotations

import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.extension.ExtensionAnnotation
import org.spockframework.runtime.model.SpecInfo

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Make sure all feature methods are annotated by {@link @RunFor}
 */
@Target([ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@ExtensionAnnotation(AllFeaturesShouldBeAnnotatedByRunForExtension.class)
@interface AllFeaturesShouldBeAnnotated {
}

@CompileStatic
class AllFeaturesShouldBeAnnotatedByRunForExtension extends AbstractAnnotationDrivenExtension<AllFeaturesShouldBeAnnotated> {
    @Override
    void visitSpecAnnotation(AllFeaturesShouldBeAnnotated runFor, SpecInfo spec) {
        if (!spec.getReflection().isAnnotationPresent(RunFor.class)) {
            spec.getFeatures()
                .findAll { !it.getFeatureMethod().getReflection().isAnnotationPresent(RunFor.class) }
                .each { throw new IllegalStateException("All feature methods should be annotated by @RunFor!") }
        }
    }
}
