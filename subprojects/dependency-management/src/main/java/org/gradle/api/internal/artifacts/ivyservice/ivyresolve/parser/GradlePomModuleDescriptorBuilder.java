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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.PomReader.PomDependencyData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomDependencyMgt;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.DefaultExclude;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor;
import org.gradle.internal.component.external.model.maven.MavenDependencyType;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This a straight copy of org.apache.ivy.plugins.parser.m2.PomModuleDescriptorBuilder, with minor changes: 1) Do not create artifact for empty classifier. (Previously did so for all non-null
 * classifiers)
 */
public class GradlePomModuleDescriptorBuilder {
    public static final ImmutableMap<String, Configuration> MAVEN2_CONFIGURATIONS = ImmutableMap.<String, Configuration>builder()
        .put("default", new Configuration("default", true, true, ImmutableSet.of("runtime", "master")))
        .put("master", new Configuration("master", true, true, ImmutableSet.of()))
        .put("compile", new Configuration("compile", true, true, ImmutableSet.of()))
        .put("provided", new Configuration("provided", true, true, ImmutableSet.of()))
        .put("runtime", new Configuration("runtime", true, true, ImmutableSet.of("compile")))
        .put("test", new Configuration("test", true, false, ImmutableSet.of("runtime")))
        .put("system", new Configuration("system", true, true, ImmutableSet.of()))
        .put("sources", new Configuration("sources", true, true, ImmutableSet.of()))
        .put("javadoc", new Configuration("javadoc", true, true, ImmutableSet.of()))
        .put("optional", new Configuration("optional", true, true, ImmutableSet.of())).build();

    private static final Map<String, MavenScope> SCOPES = ImmutableMap.<String, MavenScope>builder()
        .put("compile", MavenScope.Compile)
        .put("runtime", MavenScope.Runtime)
        .put("provided", MavenScope.Provided)
        .put("test", MavenScope.Test)
        .put("system", MavenScope.System)
        .build();

    private final VersionSelectorScheme defaultVersionSelectorScheme;
    private final VersionSelectorScheme mavenVersionSelectorScheme;

    private final List<MavenDependencyDescriptor> dependencies = Lists.newArrayList();
    private final PomReader pomReader;
    private String status;
    private ModuleComponentIdentifier componentIdentifier;

    public GradlePomModuleDescriptorBuilder(PomReader pomReader, VersionSelectorScheme gradleVersionSelectorScheme, VersionSelectorScheme mavenVersionSelectorScheme) {
        this.defaultVersionSelectorScheme = gradleVersionSelectorScheme;
        this.mavenVersionSelectorScheme = mavenVersionSelectorScheme;
        this.pomReader = pomReader;
    }

    public List<MavenDependencyDescriptor> getDependencies() {
        return dependencies;
    }

    public String getStatus() {
        return status;
    }

    public ModuleComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

    public void setModuleRevId(String group, String module, String version) {
        String effectiveVersion = MavenVersionUtils.toEffectiveVersion(version);
        status = MavenVersionUtils.inferStatusFromEffectiveVersion(version);
        componentIdentifier = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(group, module), effectiveVersion);
    }

    public void addDependency(PomDependencyData dep) {
        MavenDependencyType type = dep.isOptional() ? MavenDependencyType.OPTIONAL_DEPENDENCY : MavenDependencyType.DEPENDENCY;
        doAddDependency(dep, type);
    }

    public void addConstraint(PomDependencyMgt dep) {
        doAddDependency(dep, MavenDependencyType.DEPENDENCY_MANAGEMENT);
    }

    private void doAddDependency(PomDependencyMgt dep, MavenDependencyType dependencyType) {
        MavenScope scope;
        if (dependencyType == MavenDependencyType.DEPENDENCY_MANAGEMENT) {
            scope = MavenScope.Compile;
        } else {
            String scopeString = dep.getScope();
            if (scopeString == null || scopeString.length() == 0) {
                scopeString = getDefaultScope(dep);
            }

            // unknown scope, defaulting to 'compile'
            scope = SCOPES.getOrDefault(scopeString, MavenScope.Compile);
        }

        String version = determineVersion(dep);
        String mappedVersion = convertVersionFromMavenSyntax(version);
        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(dep.getGroupId(), dep.getArtifactId()), new DefaultImmutableVersionConstraint(mappedVersion));

        // Some POMs depend on themselves, don't add this dependency: Ivy doesn't allow this!
        // Example: http://repo2.maven.org/maven2/net/jini/jsk-platform/2.1/jsk-platform-2.1.pom
        if (selector.getModuleIdentifier().equals(componentIdentifier.getModuleIdentifier())) {
            return;
        }

        IvyArtifactName dependencyArtifact = null;
        boolean hasClassifier = dep.getClassifier() != null && dep.getClassifier().length() > 0;
        boolean hasNonJarType = dep.getType() != null && !"jar".equals(dep.getType());
        if (hasClassifier || hasNonJarType) {
            String type = "jar";
            if (dep.getType() != null) {
                type = dep.getType();
            }
            String ext = determineExtension(type);
            String classifier = hasClassifier ? dep.getClassifier() : getClassifierForType(type);

            dependencyArtifact = new DefaultIvyArtifactName(selector.getModule(), type, ext, classifier);
        }

        // experimentation shows the following, excluded modules are
        // inherited from parent POMs if either of the following is true:
        // the <exclusions> element is missing or the <exclusions> element
        // is present, but empty.
        List<ExcludeMetadata> excludes = Lists.newArrayList();
        List<ModuleIdentifier> excluded = dep.getExcludedModules();
        if (excluded.isEmpty()) {
            excluded = getDependencyMgtExclusions(dep);
        }
        for (ModuleIdentifier excludedModule : excluded) {
            DefaultExclude rule = new DefaultExclude(excludedModule);
            excludes.add(rule);
        }

        dependencies.add(new MavenDependencyDescriptor(scope, dependencyType, selector, dependencyArtifact, excludes));
    }

    private String convertVersionFromMavenSyntax(String version) {
        VersionSelector versionSelector = mavenVersionSelectorScheme.parseSelector(version);
        return defaultVersionSelectorScheme.renderSelector(versionSelector);
    }

    /**
     * Determines extension of dependency.
     *
     * @param type Type
     * @return Extension
     */
    private String determineExtension(String type) {
        return JarDependencyType.isJarExtension(type) ? "jar" : type;
    }

    /**
     * Handles special types of dependencies. If one of the following types matches, a specific type of classifier is set.
     *
     * - test-jar (see <a href="http://maven.apache.org/guides/mini/guide-attached-tests.html">Maven documentation</a>)
     * - ejb-client (see <a href="http://maven.apache.org/plugins/maven-ejb-plugin/examples/ejb-client-dependency.html">Maven documentation</a>)
     *
     * @param type Type
     */
    private String getClassifierForType(String type) {
        if(JarDependencyType.TEST_JAR.getName().equals(type)) {
            return "tests";
        } else if(JarDependencyType.EJB_CLIENT.getName().equals(type)) {
            return "client";
        }
        return null;
    }

    private enum JarDependencyType {
        TEST_JAR("test-jar"), EJB_CLIENT("ejb-client"), EJB("ejb"), BUNDLE("bundle"), MAVEN_PLUGIN("maven-plugin"), ECLIPSE_PLUGIN("eclipse-plugin");

        private static final Map<String, JarDependencyType> TYPES;

        static {
            TYPES = new HashMap<>();

            for(JarDependencyType type : values()) {
                TYPES.put(type.name, type);
            }
        }

        private final String name;

        private JarDependencyType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static boolean isJarExtension(String type) {
            return TYPES.containsKey(type);
        }
    }

    /**
     * Determines the version of a dependency. Uses the specified version if declared for the as coordinate. If the version is not declared, try to resolve it from the dependency management section.
     * In case the version cannot be resolved with any of these methods:
     * - If this is a direct dependency: throw an exception of type {@see org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.UnresolvedDependencyVersionException}.
     * - If this is an optional dependency: return the empty version
     *
     * @param dependency Dependency
     * @return Resolved dependency version
     */
    private String determineVersion(PomDependencyMgt dependency) {
        String version = dependency.getVersion();
        version = (version == null || version.length() == 0) ? getDefaultVersion(dependency) : version;
        return version == null ? "" : version;
    }

    public void addDependencyForRelocation(ModuleComponentSelector selector) {
        // Some POMs depend on themselves through their parent POM, don't add this dependency
        // since Ivy doesn't allow this!
        // Example: http://repo2.maven.org/maven2/com/atomikos/atomikos-util/3.6.4/atomikos-util-3.6.4.pom
        if (selector.getGroup().equals(componentIdentifier.getGroup())
            && selector.getModule().equals(componentIdentifier.getModule())) {
            return;
        }

        dependencies.add(new MavenDependencyDescriptor(MavenScope.Compile, MavenDependencyType.RELOCATION, selector, null, ImmutableList.of()));
    }

    private String getDefaultVersion(PomDependencyMgt dep) {
        PomDependencyMgt pomDependencyMgt = findDependencyDefault(dep);
        if (pomDependencyMgt != null) {
            return pomDependencyMgt.getVersion();
        }
        return null;
    }

    private String getDefaultScope(PomDependencyMgt dep) {
        PomDependencyMgt pomDependencyMgt = findDependencyDefault(dep);
        String result = null;
        if (pomDependencyMgt != null) {
            result = pomDependencyMgt.getScope();
        }
        if ((result == null) || !SCOPES.containsKey(result)) {
            result = "compile";
        }
        return result;
    }

    private List<ModuleIdentifier> getDependencyMgtExclusions(PomDependencyMgt dep) {
        PomDependencyMgt pomDependencyMgt = findDependencyDefault(dep);
        if (pomDependencyMgt != null) {
            return pomDependencyMgt.getExcludedModules();
        }

        return Collections.emptyList();
    }

    private PomDependencyMgt findDependencyDefault(PomDependencyMgt dependency) {
        return pomReader.findDependencyDefaults(dependency.getId());
    }
}
