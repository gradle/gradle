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

package org.gradle.tooling.internal.provider;

import org.gradle.api.internal.GradleInternal;
import org.gradle.tooling.internal.idea.DefaultIdeaProject;
import org.gradle.tooling.internal.protocol.InternalBasicIdeaProject;

/**
 * @author: Szczepan Faber, created at: 7/23/11
 */
public class BasicIdeaModelBuilder implements BuildsModel {
    public boolean canBuild(Class<?> type) {
        return type == InternalBasicIdeaProject.class;
    }

    public DefaultIdeaProject buildAll(GradleInternal gradle) {
        return new IdeaModelBuilder()
                .setOfflineDependencyResolution(true)
                .buildAll(gradle);
    }
}
