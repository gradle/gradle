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

package org.gradle.api.internal.artifacts.dsl;

import groovy.lang.Closure;
import groovy.lang.MissingMethodException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

import java.util.Arrays;
import java.util.List;

public class DefaultArtifactHandler implements ArtifactHandler {

    private final ConfigurationContainer configurationContainer;
    private final NotationParser<Object, PublishArtifact> publishArtifactFactory;

    public DefaultArtifactHandler(ConfigurationContainer configurationContainer, NotationParser<Object, PublishArtifact> publishArtifactFactory) {
        this.configurationContainer = configurationContainer;
        this.publishArtifactFactory = publishArtifactFactory;
    }

    private PublishArtifact pushArtifact(org.gradle.api.artifacts.Configuration configuration, Object notation, Closure configureClosure) {
        PublishArtifact publishArtifact = publishArtifactFactory.parseNotation(notation);
        configuration.getArtifacts().add(publishArtifact);
        ConfigureUtil.configure(configureClosure, publishArtifact);
        return publishArtifact;
    }

    public PublishArtifact add(String configurationName, Object artifactNotation) {
        return pushArtifact(configurationContainer.getByName(configurationName), artifactNotation, null);
    }

    public PublishArtifact add(String configurationName, Object artifactNotation, Closure configureClosure) {
        return pushArtifact(configurationContainer.getByName(configurationName), artifactNotation, configureClosure);
    }

    public Object methodMissing(String name, Object arg) {
        Object[] args = (Object[]) arg;
        Configuration configuration = configurationContainer.findByName(name);
        if (configuration == null) {
            throw new MissingMethodException(name, this.getClass(), args);
        }
        List<Object> normalizedArgs = GUtil.flatten(Arrays.asList(args), false);
        if (normalizedArgs.size() == 2 && normalizedArgs.get(1) instanceof Closure) {
            return pushArtifact(configuration, normalizedArgs.get(0), (Closure) normalizedArgs.get(1));
        }
        for (Object notation : args) {
            pushArtifact(configuration, notation, null);
        }
        return null;
    }
}
