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
package org.gradle.api.internal.dependencies.maven.dependencies;

import org.gradle.api.internal.dependencies.maven.dependencies.MavenDependency;
import org.gradle.api.internal.dependencies.maven.dependencies.PomModuleDescriptorDependenciesWriter;
import org.gradle.api.internal.dependencies.maven.dependencies.PomModuleDescriptorDependenciesConverter;
import org.gradle.api.internal.dependencies.maven.XmlHelper;
import org.gradle.api.internal.dependencies.maven.PomModuleDescriptorWriter;
import org.gradle.api.dependencies.maven.MavenPom;

import java.util.List;
import java.io.PrintWriter;

/**
 * @author Hans Dockter
 */
public class DefaultPomModuleDescriptorDependenciesWriter implements PomModuleDescriptorDependenciesWriter {
    private PomModuleDescriptorDependenciesConverter dependenciesConverter;

    public DefaultPomModuleDescriptorDependenciesWriter(PomModuleDescriptorDependenciesConverter dependenciesConverter) {
        this.dependenciesConverter = dependenciesConverter;
    }

    public void convert(MavenPom pom, PrintWriter printWriter) {
        List<MavenDependency> mavenDependencies = dependenciesConverter.convert(pom);
        if (mavenDependencies.size() == 0) {
            return;
        }
        printWriter.println(XmlHelper.openTag(PomModuleDescriptorWriter.DEFAULT_INDENT, DEPENDENCIES));
        for (MavenDependency mavenDependency : mavenDependencies) {
            mavenDependency.write(printWriter);
        }
        printWriter.println(XmlHelper.closeTag(PomModuleDescriptorWriter.DEFAULT_INDENT, DEPENDENCIES));
    }

    public PomModuleDescriptorDependenciesConverter getDependenciesConverter() {
        return dependenciesConverter;
    }
}
