/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.outcome.internal;

import org.gradle.api.Transformer;

public class ByTypeAndNameBuildOutcomeAssociator<T extends BuildOutcome> extends ByTypeAndCharacteristicBuildOutcomeAssociator<T> {
    public ByTypeAndNameBuildOutcomeAssociator(Class<? extends T> type) {
        super(type, new Transformer<String, T>() {
            public String transform(T outcome) {
                return outcome.getName();
            }
        });
    }
}
