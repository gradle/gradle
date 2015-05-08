/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.persist;

import com.google.common.collect.Maps;
import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.model.internal.inspect.ModelRuleExtractor;
import org.gradle.model.internal.registry.DefaultModelRegistry;
import org.gradle.model.internal.registry.ModelRegistry;

import java.util.Map;

@NotThreadSafe
public class ReusingModelRegistryStore implements ModelRegistryStore {

    private static final Logger LOGGER = Logging.getLogger(ReusingModelRegistryStore.class);

    public static final String TOGGLE = "org.gradle.model.reuse";
    public static final String BANNER = "Experimental model reuse is enabled.";

    private final ModelRuleExtractor ruleExtractor;
    private final Map<String, ModelRegistry> store = Maps.newHashMap();

    public ReusingModelRegistryStore(ModelRuleExtractor ruleExtractor) {
        this.ruleExtractor = ruleExtractor;
    }

    @Override
    public ModelRegistry get(ProjectIdentifier projectIdentifier) {
        ModelRegistry modelRegistry = store.get(projectIdentifier.getProjectDir().getAbsolutePath());
        if (modelRegistry == null) {
            LOGGER.info("creating new model registry for project: " + projectIdentifier.getPath());
            modelRegistry = new DefaultModelRegistry(ruleExtractor);
            store.put(projectIdentifier.getProjectDir().getAbsolutePath(), modelRegistry);
        } else {
            LOGGER.info("reusing model for project: " + projectIdentifier.getPath());
            // TODO - we should be doing this after the build, after the daemon has returned to the user doesn't wait for it
            modelRegistry.prepareForReuse();
        }

        return modelRegistry;
    }
}
