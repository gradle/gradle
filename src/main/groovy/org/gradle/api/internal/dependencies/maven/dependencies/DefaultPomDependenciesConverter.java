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

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.dependencies.maven.dependencies.MavenDependency;
import org.gradle.api.internal.dependencies.maven.dependencies.PomDependenciesConverter;
import org.gradle.api.dependencies.maven.MavenPom;
import org.gradle.util.WrapUtil;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Hans Dockter
 */
public class DefaultPomDependenciesConverter implements PomDependenciesConverter {
    private ExcludeRuleConverter excludeRuleConverter;

    public DefaultPomDependenciesConverter(ExcludeRuleConverter excludeRuleConverter) {
        this.excludeRuleConverter = excludeRuleConverter;
    }

    public List<MavenDependency> convert(MavenPom pom) {
        List<MavenDependency> mavenDependencies = new ArrayList<MavenDependency>();
        for (DependencyDescriptor dependencyDescriptor : pom.getDependencies()) {
            if (dependencyDescriptor.getAllDependencyArtifacts().length == 0) {
                addFromDependencyDescriptor(mavenDependencies, pom, dependencyDescriptor);
            } else {
                addFromArtifactDescriptor(mavenDependencies, pom, dependencyDescriptor);
            }
        }
        return mavenDependencies;
    }

    private void addFromArtifactDescriptor(List<MavenDependency> mavenDependencies, MavenPom pom, DependencyDescriptor dependencyDescriptor) {
        for (DependencyArtifactDescriptor artifactDescriptor : dependencyDescriptor.getAllDependencyArtifacts()) {
            String scope = pom.getScopeMappings().getScope(getArtifactConfigurations(dependencyDescriptor, artifactDescriptor));
            if (useScope(pom, scope)) {
                return;
            }
            mavenDependencies.add(createMavenDependencyFromArtifactDescriptor(dependencyDescriptor, artifactDescriptor, scope));
        }
    }

    private String[] getArtifactConfigurations(DependencyDescriptor dependencyDescriptor, DependencyArtifactDescriptor artifactDescriptor) {
        List<String> configurations = new ArrayList<String>();
        for (String configuration : dependencyDescriptor.getModuleConfigurations()) {
            if (Arrays.asList(dependencyDescriptor.getDependencyArtifacts(configuration)).contains(artifactDescriptor)) {
                configurations.add(configuration);
            }
        }
        return configurations.toArray(new String[configurations.size()]);
    }

    private void addFromDependencyDescriptor(List<MavenDependency> mavenDependencies, MavenPom pom, DependencyDescriptor dependencyDescriptor) {
        String scope = pom.getScopeMappings().getScope(dependencyDescriptor.getModuleConfigurations());
        if (useScope(pom, scope)) {
            return;
        }
        mavenDependencies.add(createMavenDependencyFromDependencyDescriptor(dependencyDescriptor, scope));
    }

    private boolean useScope(MavenPom pom, String scope) {
        return scope == null && pom.getScopeMappings().isSkipUnmappedConfs();
    }

    private MavenDependency addExclude(DependencyDescriptor dependencyDescriptor, MavenDependency mavenDependency) {
        if (dependencyDescriptor.canExclude()) {
            for (ExcludeRule excludeRule : dependencyDescriptor.getExcludeRules(dependencyDescriptor.getModuleConfigurations())) {
                DefaultMavenExclude mavenExclude = excludeRuleConverter.convert(excludeRule);
                if (mavenExclude != null) {
                    mavenDependency.getMavenExcludes().add(mavenExclude);
                }
            }
        }
        return mavenDependency;
    }

    private MavenDependency createMavenDependencyFromArtifactDescriptor(DependencyDescriptor dependencyDescriptor, DependencyArtifactDescriptor artifactDescriptor, String scope) {
        return createMavenDependency(dependencyDescriptor, artifactDescriptor.getName(), artifactDescriptor.getType(), scope);
    }

    private MavenDependency createMavenDependencyFromDependencyDescriptor(DependencyDescriptor dependencyDescriptor, String scope) {
        ModuleRevisionId mrid = dependencyDescriptor.getDependencyRevisionId();
        return createMavenDependency(dependencyDescriptor, mrid.getName(), null, scope);
    }

    private MavenDependency createMavenDependency(DependencyDescriptor dependencyDescriptor, String name, String type, String scope) {
        ModuleRevisionId mrid = dependencyDescriptor.getDependencyRevisionId();
        return addExclude(dependencyDescriptor, DefaultMavenDependency.newInstance(mrid.getOrganisation(), name, mrid.getRevision(), type, scope));
    }

    public ExcludeRuleConverter getExcludeRuleConverter() {
        return excludeRuleConverter;
    }
}
