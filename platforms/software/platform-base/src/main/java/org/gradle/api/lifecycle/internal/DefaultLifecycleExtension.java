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

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.lifecycle.LifecycleExtension;
import org.gradle.api.lifecycle.LifecycleStage;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

public class DefaultLifecycleExtension implements LifecycleExtension {
    private final NamedDomainObjectContainer<LifecycleStage> stages;

    @Inject
    public DefaultLifecycleExtension(ObjectFactory objectFactory) {
        this.stages = objectFactory.domainObjectContainer(LifecycleStage.class, name -> objectFactory.newInstance(LifecycleStageInternal.class, name));
    }

    @Override
    public NamedDomainObjectContainer<LifecycleStage> getStages() {
        return stages;
    }
}
