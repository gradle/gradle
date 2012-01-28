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
package org.gradle.api.publication.maven.internal;

import org.gradle.api.artifacts.maven.*;
import org.gradle.internal.Factory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.publication.maven.internal.ant.DefaultExcludeRuleConverter;
import org.gradle.api.publication.maven.internal.ant.DefaultPomDependenciesConverter;

import java.util.Map;

public class DefaultMavenFactory implements MavenFactory {

    public Factory<MavenPom> createMavenPomFactory(ConfigurationContainer configurationContainer, Conf2ScopeMappingContainer conf2ScopeMappingContainer, FileResolver fileResolver) {
        return new DefaultMavenPomFactory(configurationContainer, conf2ScopeMappingContainer, createPomDependenciesConverter(), fileResolver);
    }

    public Factory<MavenPom> createMavenPomFactory(ConfigurationContainer configurationContainer, Map<Configuration, Conf2ScopeMapping> mappings, FileResolver fileResolver) {
        return new DefaultMavenPomFactory(configurationContainer, createConf2ScopeMappingContainer(mappings), createPomDependenciesConverter(), fileResolver);
    }

    private PomDependenciesConverter createPomDependenciesConverter() {
        return new DefaultPomDependenciesConverter(new DefaultExcludeRuleConverter());
    }

    public Conf2ScopeMappingContainer createConf2ScopeMappingContainer(Map<Configuration, Conf2ScopeMapping> mappings) {
        return new DefaultConf2ScopeMappingContainer(mappings);
    }
}
