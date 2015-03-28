/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.publication.maven.internal.pom;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.Factory;

public class DefaultMavenPomFactory implements Factory<MavenPom> {
    private ConfigurationContainer configurationContainer;
    private Conf2ScopeMappingContainer conf2ScopeMappingContainer;
    private PomDependenciesConverter pomDependenciesConverter;
    private FileResolver fileResolver;


    public DefaultMavenPomFactory(ConfigurationContainer configurationContainer, Conf2ScopeMappingContainer conf2ScopeMappingContainer, PomDependenciesConverter pomDependenciesConverter,
                                  FileResolver fileResolver) {
        this.configurationContainer = configurationContainer;
        this.conf2ScopeMappingContainer = conf2ScopeMappingContainer;
        this.pomDependenciesConverter = pomDependenciesConverter;
        this.fileResolver = fileResolver;
    }

    public MavenPom create() {
        return new DefaultMavenPom(configurationContainer,
                new DefaultConf2ScopeMappingContainer(conf2ScopeMappingContainer.getMappings()), pomDependenciesConverter, fileResolver);
    }
}
