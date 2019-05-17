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

public class ByTypeAndCharacteristicBuildOutcomeAssociator<T extends BuildOutcome> implements BuildOutcomeAssociator {

    private final Class<? extends T> type;
    private final Transformer<?, T> characteristicTransformer;

    public ByTypeAndCharacteristicBuildOutcomeAssociator(Class<? extends T> type, Transformer<?, T> characteristicTransformer) {
        this.type = type;
        this.characteristicTransformer = characteristicTransformer;
    }

    @Override
    public Class<? extends BuildOutcome> findAssociationType(BuildOutcome source, BuildOutcome target) {
        if (type.isInstance(source) && type.isInstance(target)) {
            Object fromCharacteristic = characteristicTransformer.transform(type.cast(source));
            Object toCharacteristic = characteristicTransformer.transform(type.cast(target));

            if (fromCharacteristic == null && toCharacteristic == null) {
                return type;
            } else if (fromCharacteristic == null || toCharacteristic == null) {
                return null;
            } else if (fromCharacteristic.equals(toCharacteristic)) {
                return type;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
}
