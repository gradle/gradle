/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.lifecycle.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.lifecycle.LifecycleStage;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.util.stream.Collectors;

public interface LifecycleStageInternal extends LifecycleStage {
    @Inject
    ProviderFactory getProviderFactory();
    @Inject
    ObjectFactory getObjectFactory();

    @Override
    default Provider<FileCollection> getAllOutputs() {
        return getProviderFactory().provider(() -> {
            ConfigurableFileCollection collectedMemberOutputs = getObjectFactory().fileCollection();
            collectedMemberOutputs.from(
                getMembers().map(
                    lifecycleStages -> lifecycleStages.stream()
                        .map(LifecycleStage::getAllOutputs)
                        .collect(Collectors.toSet())
                )
            );
            return getOutputs().plus(collectedMemberOutputs);
        });
    }
}
