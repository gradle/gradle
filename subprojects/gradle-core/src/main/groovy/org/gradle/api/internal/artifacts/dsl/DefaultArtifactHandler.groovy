/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.util.GUtil
import org.gradle.util.ConfigureUtil

/**
 * @author Hans Dockter
 */
class DefaultArtifactHandler implements ArtifactHandler {

    ConfigurationContainer configurationContainer
    PublishArtifactFactory publishArtifactFactory

    def DefaultArtifactHandler(ConfigurationContainer configurationContainer, PublishArtifactFactory publishArtifactFactory) {
        this.configurationContainer = configurationContainer;
        this.publishArtifactFactory = publishArtifactFactory;
    }

    private PublishArtifact pushArtifact(org.gradle.api.artifacts.Configuration configuration, Object notation, Closure configureClosure) {
        PublishArtifact publishArtifact
        if (notation instanceof PublishArtifact) {
            publishArtifact = notation
        } else {
            publishArtifact = publishArtifactFactory.createArtifact(notation)
        }
        configuration.addArtifact(publishArtifact)
        ConfigureUtil.configure(configureClosure, publishArtifact)
        return publishArtifact
    }

    public def methodMissing(String name, args) {
        Configuration configuration = configurationContainer.findByName(name)
        if (configuration == null) {
            if (!getMetaClass().respondsTo(this, name, args.size())) {
                throw new MissingMethodException(name, this.getClass(), args);
            }
        }
        Object[] normalizedArgs = GUtil.flatten(args as List, false)
        if (normalizedArgs.length == 2 && normalizedArgs[1] instanceof Closure) {
            return pushArtifact(configuration, normalizedArgs[0], (Closure) normalizedArgs[1])
        } 
        args.each {notation ->
            pushArtifact(configuration, notation, null)
        }
    }
}
