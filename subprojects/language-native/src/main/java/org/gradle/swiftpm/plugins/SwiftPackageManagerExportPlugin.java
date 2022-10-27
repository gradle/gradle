/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.swiftpm.plugins;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.SubVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionRangeSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.provider.Provider;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.language.swift.SwiftVersion;
import org.gradle.nativeplatform.Linkage;
import org.gradle.swiftpm.Package;
import org.gradle.swiftpm.internal.AbstractProduct;
import org.gradle.swiftpm.internal.BranchDependency;
import org.gradle.swiftpm.internal.DefaultExecutableProduct;
import org.gradle.swiftpm.internal.DefaultLibraryProduct;
import org.gradle.swiftpm.internal.DefaultPackage;
import org.gradle.swiftpm.internal.DefaultTarget;
import org.gradle.swiftpm.internal.Dependency;
import org.gradle.swiftpm.internal.SwiftPmTarget;
import org.gradle.swiftpm.internal.VersionDependency;
import org.gradle.swiftpm.tasks.GenerateSwiftPackageManagerManifest;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.git.GitVersionControlSpec;
import org.gradle.vcs.internal.VcsResolver;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * A plugin that produces a Swift Package Manager manifests from the Gradle model.
 *
 * <p>This plugin should only be applied to the root project of a build.</p>
 *
 * @since 4.6
 */
public abstract class SwiftPackageManagerExportPlugin implements Plugin<Project> {
    private final VcsResolver vcsResolver;
    private final VersionSelectorScheme versionSelectorScheme;
    private final ProjectDependencyPublicationResolver publicationResolver;
    private final VersionParser versionParser;

    @Inject
    public SwiftPackageManagerExportPlugin(VcsResolver vcsResolver, VersionSelectorScheme versionSelectorScheme, ProjectDependencyPublicationResolver publicationResolver, VersionParser versionParser) {
        this.vcsResolver = vcsResolver;
        this.versionSelectorScheme = versionSelectorScheme;
        this.publicationResolver = publicationResolver;
        this.versionParser = versionParser;
    }

    @Override
    public void apply(final Project project) {
        final GenerateSwiftPackageManagerManifest manifestTask = project.getTasks().create("generateSwiftPmManifest", GenerateSwiftPackageManagerManifest.class);
        manifestTask.getManifestFile().set(project.getLayout().getProjectDirectory().file("Package.swift"));

        // Defer attaching the model until all components have been (most likely) configured
        // TODO - make this relationship explicit to make this more reliable and offer better diagnostics
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                Provider<Package> products = project.getProviders().provider(new MemoizingCallable(new PackageFactory(project)));
                manifestTask.getPackage().set(products);
            }
        });
    }

    private static class MemoizingCallable implements Callable<Package> {
        private Package result;
        private Callable<Package> delegate;

        MemoizingCallable(Callable<Package> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Package call() throws Exception {
            if (result == null) {
                result = delegate.call();
                delegate = null;
            }
            return result;
        }
    }

    private class PackageFactory implements Callable<Package> {
        private final Project project;

        PackageFactory(Project project) {
            this.project = project;
        }

        @Override
        public Package call() {
            Set<AbstractProduct> products = new LinkedHashSet<AbstractProduct>();
            List<DefaultTarget> targets = new ArrayList<DefaultTarget>();
            List<Dependency> dependencies = new ArrayList<Dependency>();
            SwiftVersion swiftLanguageVersion = null;
            for (Project p : project.getAllprojects()) {
                for (CppApplication application : p.getComponents().withType(CppApplication.class)) {
                    DefaultTarget target = new DefaultTarget(application.getBaseName().get(), p.getProjectDir(), application.getCppSource());
                    collectDependencies(application.getImplementationDependencies(), dependencies, target);
                    DefaultExecutableProduct product = new DefaultExecutableProduct(p.getName(), target);
                    // TODO - set header dir for applications
                    products.add(product);
                    targets.add(target);
                }
                for (CppLibrary library : p.getComponents().withType(CppLibrary.class)) {
                    DefaultTarget target = new DefaultTarget(library.getBaseName().get(), p.getProjectDir(), library.getCppSource());
                    collectDependencies(library.getImplementationDependencies(), dependencies, target);
                    Set<File> headerDirs = library.getPublicHeaderDirs().getFiles();
                    if (!headerDirs.isEmpty()) {
                        // TODO - deal with more than one directory
                        target.setPublicHeaderDir(headerDirs.iterator().next());
                    }
                    targets.add(target);

                    if (library.getLinkage().get().contains(Linkage.SHARED)) {
                        products.add(new DefaultLibraryProduct(p.getName(), target, Linkage.SHARED));
                    }
                    if (library.getLinkage().get().contains(Linkage.STATIC)) {
                        products.add(new DefaultLibraryProduct(p.getName() + "Static", target, Linkage.STATIC));
                    }
                }
                for (SwiftApplication application : p.getComponents().withType(SwiftApplication.class)) {
                    DefaultTarget target = new DefaultTarget(application.getModule().get(), p.getProjectDir(), application.getSwiftSource());
                    swiftLanguageVersion = max(swiftLanguageVersion, application.getSourceCompatibility().getOrNull());
                    collectDependencies(application.getImplementationDependencies(), dependencies, target);
                    DefaultExecutableProduct product = new DefaultExecutableProduct(p.getName(), target);
                    products.add(product);
                    targets.add(target);
                }
                for (SwiftLibrary library : p.getComponents().withType(SwiftLibrary.class)) {
                    DefaultTarget target = new DefaultTarget(library.getModule().get(), p.getProjectDir(), library.getSwiftSource());
                    swiftLanguageVersion = max(swiftLanguageVersion, library.getSourceCompatibility().getOrNull());
                    collectDependencies(library.getImplementationDependencies(), dependencies, target);
                    targets.add(target);

                    if (library.getLinkage().get().contains(Linkage.SHARED)) {
                        products.add(new DefaultLibraryProduct(p.getName(), target, Linkage.SHARED));
                    }
                    if (library.getLinkage().get().contains(Linkage.STATIC)) {
                        products.add(new DefaultLibraryProduct(p.getName() + "Static", target, Linkage.STATIC));
                    }
                }
            }
            return new DefaultPackage(products, targets, dependencies, swiftLanguageVersion);
        }

        private SwiftVersion max(SwiftVersion v1, SwiftVersion v2) {
            if (v1 == null) {
                return v2;
            }
            if (v2 == null) {
                return v1;
            }
            if (v1.compareTo(v2) > 0) {
                return v1;
            }
            return v2;
        }

        private void collectDependencies(Configuration configuration, Collection<Dependency> dependencies, DefaultTarget target) {
            for (org.gradle.api.artifacts.Dependency dependency : configuration.getAllDependencies()) {
                if (dependency instanceof ProjectDependency) {
                    ProjectDependency projectDependency = (ProjectDependency) dependency;
                    SwiftPmTarget identifier = publicationResolver.resolve(SwiftPmTarget.class, projectDependency);
                    target.getRequiredTargets().add(identifier.getTargetName());
                } else if (dependency instanceof ExternalModuleDependency) {
                    ExternalModuleDependency externalDependency = (ExternalModuleDependency) dependency;
                    ModuleComponentSelector depSelector = DefaultModuleComponentSelector.newSelector(externalDependency);
                    VersionControlSpec vcsSpec = vcsResolver.locateVcsFor(depSelector);
                    if (vcsSpec == null || !(vcsSpec instanceof GitVersionControlSpec)) {
                        throw new InvalidUserDataException(String.format("Cannot determine the Git URL for dependency on %s:%s.", dependency.getGroup(), dependency.getName()));
                    }
                    GitVersionControlSpec gitSpec = (GitVersionControlSpec) vcsSpec;
                    dependencies.add(toSwiftPmDependency(externalDependency, gitSpec));
                    target.getRequiredProducts().add(externalDependency.getName());
                } else {
                    throw new InvalidUserDataException(String.format("Cannot map a dependency of type %s (%s)", dependency.getClass().getSimpleName(), dependency));
                }
            }
        }

        private Dependency toSwiftPmDependency(ExternalModuleDependency externalDependency, GitVersionControlSpec gitSpec) {
            if (externalDependency.getVersionConstraint().getBranch() != null) {
                if (externalDependency.getVersion() != null) {
                    throw new InvalidUserDataException(String.format("Cannot map a dependency on %s:%s that defines both a branch (%s) and a version constraint (%s).", externalDependency.getGroup(), externalDependency.getName(), externalDependency.getVersionConstraint().getBranch(), externalDependency.getVersion()));
                }
                return new BranchDependency(gitSpec.getUrl(), externalDependency.getVersionConstraint().getBranch());
            }

            String versionSelectorString = externalDependency.getVersion();
            VersionSelector versionSelector = versionSelectorScheme.parseSelector(versionSelectorString);
            if (versionSelector instanceof LatestVersionSelector) {
                LatestVersionSelector latestVersionSelector = (LatestVersionSelector) versionSelector;
                if (latestVersionSelector.getSelectorStatus().equals("integration")) {
                    return new BranchDependency(gitSpec.getUrl(), "master");
                }
            } else if (versionSelector instanceof ExactVersionSelector) {
                return new VersionDependency(gitSpec.getUrl(), versionSelector.getSelector());
            } else if (versionSelector instanceof VersionRangeSelector) {
                VersionRangeSelector versionRangeSelector = (VersionRangeSelector) versionSelector;
                if (versionRangeSelector.isLowerInclusive()) {
                    return new VersionDependency(gitSpec.getUrl(), versionRangeSelector.getLowerBound(), versionRangeSelector.getUpperBound(), versionRangeSelector.isUpperInclusive());
                }
            } else if (versionSelector instanceof SubVersionSelector) {
                SubVersionSelector subVersionSelector = (SubVersionSelector) versionSelector;
                String prefix = subVersionSelector.getPrefix();
                // TODO - take care of this in the selector parser
                if (prefix.endsWith(".")) {
                    String versionString = prefix.substring(0, prefix.length() - 1);
                    Version version = versionParser.transform(versionString);
                    if (version.getNumericParts().length == 1) {
                        Long part1 = version.getNumericParts()[0];
                        return new VersionDependency(gitSpec.getUrl(), part1 + ".0.0");
                    }
                    if (version.getNumericParts().length == 2) {
                        Long part1 = version.getNumericParts()[0];
                        Long part2 = version.getNumericParts()[1];
                        return new VersionDependency(gitSpec.getUrl(), part1 + "." + part2 + ".0", part1 + "." + (part2 + 1) + ".0", false);
                    }
                }
            }
            throw new InvalidUserDataException(String.format("Cannot map a dependency on %s:%s with version constraint (%s).", externalDependency.getGroup(), externalDependency.getName(), externalDependency.getVersion()));
        }
    }
}
