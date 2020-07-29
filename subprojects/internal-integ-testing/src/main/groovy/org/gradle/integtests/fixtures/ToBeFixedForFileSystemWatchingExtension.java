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

package org.gradle.integtests.fixtures;

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter;
import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.ISkippable;
import org.spockframework.runtime.model.SpecInfo;

public class ToBeFixedForFileSystemWatchingExtension extends AbstractAnnotationDrivenExtension<ToBeFixedForFileSystemWatching> {
    @Override
    public void visitSpecAnnotation(ToBeFixedForFileSystemWatching annotation, SpecInfo spec) {
        doVisit(spec, annotation);
    }

    @Override
    public void visitFeatureAnnotation(ToBeFixedForFileSystemWatching annotation, FeatureInfo feature) {
        doVisit(feature, annotation);
    }

    private void doVisit(ISkippable skippable, ToBeFixedForFileSystemWatching annotation) {
        if (GradleContextualExecuter.isWatchFs() && annotation.failsOnlyIf().isFulfilled()) {
            skippable.setSkipped(true);
        }
    }
}
