/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.JvmTestSuitePlugin;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.plugins.internal.JavaPluginHelper;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.idea.internal.IdeaModuleInternal;
import org.gradle.plugins.ide.idea.internal.IdeaModuleMetadata;
import org.gradle.plugins.ide.idea.internal.IdeaModuleSupport;
import org.gradle.plugins.ide.idea.internal.IdeaProjectInternal;
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaModuleIml;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.gradle.plugins.ide.idea.model.IdeaWorkspace;
import org.gradle.plugins.ide.idea.model.PathFactory;
import org.gradle.plugins.ide.idea.model.internal.GeneratedIdeaScope;
import org.gradle.plugins.ide.idea.model.internal.IdeaDependenciesProvider;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.IdePlugin;
import org.gradle.plugins.ide.internal.IdePluginHelper;
import org.gradle.plugins.ide.internal.configurer.UniqueProjectNameProvider;
import org.gradle.testing.base.TestingExtension;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Adds a GenerateIdeaModule task. When applied to a root project, also adds a GenerateIdeaProject task. For projects that have the Java plugin applied, the tasks receive additional Java-specific
 * configuration.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/idea_plugin.html">IDEA plugin reference</a>
 */
public abstract class IdeaPlugin extends IdePlugin {
    private static final Predicate<Project> HAS_IDEA_AND_JAVA_PLUGINS = new Predicate<Project>() {
        @Override
        public boolean apply(Project project) {
            return project.getPlugins().hasPlugin(IdeaPlugin.class) && project.getPlugins().hasPlugin(JavaBasePlugin.class);
        }
    };
    public static final Function<Project, JavaVersion> SOURCE_COMPATIBILITY = new Function<Project, JavaVersion>() {
        @Override
        public JavaVersion apply(Project p) {
            return p.getExtensions().getByType(JavaPluginExtension.class).getSourceCompatibility();
        }
    };
    public static final Function<Project, JavaVersion> TARGET_COMPATIBILITY = new Function<Project, JavaVersion>() {
        @Override
        public JavaVersion apply(Project p) {
            return p.getExtensions().getByType(JavaPluginExtension.class).getTargetCompatibility();
        }
    };

    private IdeaModel ideaModel;
    private List<Project> allJavaProjects;
    private final UniqueProjectNameProvider uniqueProjectNameProvider;
    private final IdeArtifactRegistry artifactRegistry;

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    public IdeaPlugin(UniqueProjectNameProvider uniqueProjectNameProvider, IdeArtifactRegistry artifactRegistry) {
        this.uniqueProjectNameProvider = uniqueProjectNameProvider;
        this.artifactRegistry = artifactRegistry;
    }

    public IdeaModel getModel() {
        return ideaModel;
    }

    @Override
    protected String getLifecycleTaskName() {
        return "idea";
    }

    @Override
    protected boolean registersLifecycleTasks() {
        return false;
    }

    // TODO: decide what to do with the scala/idea plugin combination. Removing IdeaScalaConfigurer (whose output
    //  only reached the generated ipr/iml files) made the two plugins fully independent — potentially something to
    //  document. It also means idea+scala can now be used with Isolated Projects: the failure guarding the
    //  cross-project Scala SDK wiring is gone together with that wiring.
    @Override
    protected void onApply(final Project project) {
        ideaModel = project.getExtensions().create("idea", IdeaModel.class);

        configureIdeaWorkspace();
        configureIdeaProject(project);
        configureIdeaModule((ProjectInternal) project);
        configureForJavaPlugin(project);
        configureForWarPlugin(project);
        configureForTestSuitesPlugin(project);
    }

    @SuppressWarnings("deprecation")
    private void configureIdeaWorkspace() {
        final IdeaWorkspace workspace = DeprecationLogger.whileDisabled(
            () -> {
                IdeaWorkspace iw = getObjectFactory().newInstance(IdeaWorkspace.class);
                ideaModel.setWorkspace(iw);
                return iw;
            }
        );

        if (isRoot()) {
            workspace.setIws(new XmlFileContentMerger(new XmlTransformer()));
        }
    }

    private void configureIdeaProject(final Project project) {
        if (isRoot()) {
            XmlFileContentMerger ipr = new XmlFileContentMerger(new XmlTransformer());
            // Instantiating an internal subclass is required for Isolated Projects-safe model building
            final IdeaProject ideaProject = getObjectFactory().newInstance(IdeaProjectInternal.class, project, ipr);
            ideaModel.setProject(ideaProject);

            ideaProject.setOutputFile(new File(project.getProjectDir(), project.getName() + ".ipr"));
            ConventionMapping conventionMapping = ((IConventionAware) ideaProject).getConventionMapping();
            conventionMapping.map("jdkName", new Callable<String>() {
                @Override
                public String call() {
                    return JavaVersion.current().toString();
                }
            });
            conventionMapping.map("languageLevel", new Callable<IdeaLanguageLevel>() {
                @Override
                public IdeaLanguageLevel call() {
                    JavaVersion maxSourceCompatibility = getMaxJavaModuleCompatibilityVersionFor(SOURCE_COMPATIBILITY);
                    return new IdeaLanguageLevel(maxSourceCompatibility);
                }

            });
            conventionMapping.map("targetBytecodeVersion", new Callable<JavaVersion>() {
                @Override
                public JavaVersion call() {
                    return getMaxJavaModuleCompatibilityVersionFor(TARGET_COMPATIBILITY);
                }

            });

            ideaProject.getWildcards().addAll(Arrays.asList("!?*.class", "!?*.scala", "!?*.groovy", "!?*.java"));
            conventionMapping.map("modules", new Callable<List<IdeaModule>>() {
                @Override
                public List<IdeaModule> call() {
                    return Lists.newArrayList(Iterables.transform(Sets.filter(project.getRootProject().getAllprojects(), new Predicate<Project>() {
                        @Override
                        public boolean apply(Project p) {
                            return p.getPlugins().hasPlugin(IdeaPlugin.class);
                        }

                    }), new Function<Project, IdeaModule>() {
                        @Override
                        public IdeaModule apply(Project p) {
                            return ideaModelFor(p).getModule();
                        }
                    }));
                }
            });

            conventionMapping.map("pathFactory", new Callable<PathFactory>() {
                @Override
                public PathFactory call() {
                    return new PathFactory().addPathVariable("PROJECT_DIR", ideaProject.getOutputFile().getParentFile());
                }
            });
        }
    }

    private static IdeaModel ideaModelFor(Project project) {
        return project.getExtensions().getByType(IdeaModel.class);
    }

    private JavaVersion getMaxJavaModuleCompatibilityVersionFor(Function<Project, JavaVersion> toJavaVersion) {
        List<Project> allJavaProjects = getAllJavaProjects();
        if (allJavaProjects.isEmpty()) {
            return IdeaModuleSupport.FALLBACK_MODULE_JAVA_COMPATIBILITY_VERSION;
        } else {
            return Collections.max(Lists.transform(allJavaProjects, toJavaVersion));
        }
    }

    private List<Project> getAllJavaProjects() {
        if (allJavaProjects != null) {
            // cache result because it is pretty expensive to compute
            return allJavaProjects;
        }
        allJavaProjects = Lists.newArrayList(Iterables.filter(project.getRootProject().getAllprojects(), HAS_IDEA_AND_JAVA_PLUGINS));
        return allJavaProjects;
    }

    @SuppressWarnings("deprecation")
    private void configureIdeaModule(final ProjectInternal project) {
        // Instantiating an internal subclass is required for Isolated Projects-safe model building
        final IdeaModule module = DeprecationLogger.whileDisabled(() ->
            getObjectFactory().newInstance(IdeaModuleInternal.class, project, new IdeaModuleIml(new XmlTransformer(), project.getProjectDir()))
        );
        ideaModel.setModule(module);

        final String defaultModuleName = uniqueProjectNameProvider.getUniqueName(project.getProjectIdentity());
        module.setName(defaultModuleName);

        ConventionMapping conventionMapping = ((IConventionAware) module).getConventionMapping();
        Set<File> sourceDirs = new LinkedHashSet<>();
        conventionMapping.map("sourceDirs", new Callable<Set<File>>() {
            @Override
            public Set<File> call() {
                return sourceDirs;
            }
        });
        conventionMapping.map("contentRoot", new Callable<File>() {
            @Override
            public File call() {
                return project.getProjectDir();
            }
        });
        Set<File> resourceDirs = new LinkedHashSet<>();
        conventionMapping.map("resourceDirs", new Callable<Set<File>>() {
            @Override
            public Set<File> call() throws Exception {
                return resourceDirs;
            }
        });
        Set<File> excludeDirs = new LinkedHashSet<>();
        conventionMapping.map("excludeDirs", new Callable<Set<File>>() {
            @Override
            public Set<File> call() {
                // ".gradle" is the default project cache dir name (see BuildScopeCacheDir). Hardcoding it here is a
                // historical accident: it should honor the user-configurable --project-cache-dir instead.
                // We deliberately leave it as-is, as this IDE model generation is scheduled for removal in Gradle 10.
                excludeDirs.add(project.file(".gradle"));
                excludeDirs.add(project.getLayout().getBuildDirectory().getAsFile().get());
                return excludeDirs;
            }
        });

        conventionMapping.map("pathFactory", new Callable<PathFactory>() {
            @Override
            public PathFactory call() {
                final PathFactory factory = new PathFactory();
                factory.addPathVariable("MODULE_DIR", module.getOutputFile().getParentFile());
                for (Map.Entry<String, File> entry : module.getPathVariables().entrySet()) {
                    factory.addPathVariable(entry.getKey(), entry.getValue());
                }
                return factory;
            }

        });

        artifactRegistry.registerIdeProject(new IdeaModuleMetadata(module));
    }

    private void configureForJavaPlugin(final Project project) {
        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
            @Override
            public void execute(JavaPlugin javaPlugin) {
                configureIdeaModuleForJava(project);
            }
        });
    }

    private void configureForWarPlugin(final Project project) {
        project.getPlugins().withType(WarPlugin.class, new Action<WarPlugin>() {
            @Override
            public void execute(WarPlugin warPlugin) {
                configureIdeaModuleForWar(project);
            }
        });
    }

    private void configureForTestSuitesPlugin(final Project project) {
        project.getPlugins().withType(JvmTestSuitePlugin.class, new Action<JvmTestSuitePlugin>() {
            @Override
            public void execute(JvmTestSuitePlugin testSuitePlugin) {
                configureIdeaModuleForTestSuites(project);
            }
        });
    }

    private void configureIdeaModuleForJava(final Project project) {
        JvmFeatureInternal mainFeature = JavaPluginHelper.getJavaComponent(project).getMainFeature();
        JvmTestSuite defaultTestSuite = JavaPluginHelper.getDefaultTestSuite(project);

        // Defaults
        setupScopes(mainFeature, defaultTestSuite);

        // Convention
        ConventionMapping convention = ((IConventionAware) ideaModel.getModule()).getConventionMapping();
        Set<File> sourceDirs = new LinkedHashSet<>();
        convention.map("sourceDirs", new Callable<Set<File>>() {
            @Override
            public Set<File> call() {
                SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
                sourceDirs.addAll(sourceSets.getByName("main").getAllJava().getSrcDirs());
                return sourceDirs;
            }
        });
        Set<File> resourceDirs = new LinkedHashSet<>();
        convention.map("resourceDirs", new Callable<Set<File>>() {
            @Override
            public Set<File> call() {
                SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
                resourceDirs.addAll(sourceSets.getByName("main").getResources().getSrcDirs());
                return resourceDirs;
            }
        });

        Map<String, FileCollection> singleEntryLibraries = new LinkedHashMap<String, FileCollection>(2);
        convention.map("singleEntryLibraries", new Callable<Map<String, FileCollection>>() {
            @Override
            public Map<String, FileCollection> call() {
                SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
                singleEntryLibraries.putIfAbsent("RUNTIME", sourceSets.getByName("main").getOutput().getDirs());
                singleEntryLibraries.putIfAbsent("TEST", sourceSets.getByName("test").getOutput().getDirs());
                return singleEntryLibraries;
            }

        });
        convention.map("targetBytecodeVersion", new Callable<JavaVersion>() {
            @Override
            public JavaVersion call() {
                JavaVersion moduleTargetBytecodeLevel = project.getExtensions().getByType(JavaPluginExtension.class).getTargetCompatibility();
                return includeModuleBytecodeLevelOverride(project.getRootProject(), moduleTargetBytecodeLevel) ? moduleTargetBytecodeLevel : null;
            }

        });
        convention.map("languageLevel", new Callable<IdeaLanguageLevel>() {
            @Override
            public IdeaLanguageLevel call() {
                IdeaLanguageLevel moduleLanguageLevel = new IdeaLanguageLevel(project.getExtensions().getByType(JavaPluginExtension.class).getSourceCompatibility());
                return includeModuleLanguageLevelOverride(project.getRootProject(), moduleLanguageLevel) ? moduleLanguageLevel : null;
            }

        });
    }

    private void setupScopes(JvmFeatureInternal mainFeature, JvmTestSuite defaultTestSuite) {
        Map<String, Map<String, Collection<Configuration>>> scopes = new LinkedHashMap<>();
        for (GeneratedIdeaScope scope : GeneratedIdeaScope.values()) {
            Map<String, Collection<Configuration>> plusMinus = new LinkedHashMap<>();
            plusMinus.put(IdeaDependenciesProvider.SCOPE_PLUS, new ArrayList<>());
            plusMinus.put(IdeaDependenciesProvider.SCOPE_MINUS, new ArrayList<>());
            scopes.put(scope.name(), plusMinus);
        }

        Collection<Configuration> provided = scopes.get(GeneratedIdeaScope.PROVIDED.name()).get(IdeaDependenciesProvider.SCOPE_PLUS);
        provided.add(mainFeature.getCompileClasspathConfiguration());

        Collection<Configuration> runtime = scopes.get(GeneratedIdeaScope.RUNTIME.name()).get(IdeaDependenciesProvider.SCOPE_PLUS);
        runtime.add(mainFeature.getRuntimeClasspathConfiguration());

        ConfigurationContainer configurations = project.getConfigurations();
        Collection<Configuration> test = scopes.get(GeneratedIdeaScope.TEST.name()).get(IdeaDependenciesProvider.SCOPE_PLUS);
        test.add(configurations.getByName(defaultTestSuite.getSources().getCompileClasspathConfigurationName()));
        test.add(configurations.getByName(defaultTestSuite.getSources().getRuntimeClasspathConfigurationName()));

        ideaModel.getModule().setScopes(scopes);
    }

    private void configureIdeaModuleForTestSuites(final Project project) {
        final TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
        final IdeaModule ideaModule = ideaModelFor(project).getModule();
        testing.getSuites().withType(JvmTestSuite.class).configureEach(suite -> {
            ideaModule.getTestSources().from(suite.getSources().getAllJava().getSourceDirectories());
            ideaModule.getTestResources().from(suite.getSources().getResources().getSourceDirectories());
        });
    }

    private void configureIdeaModuleForWar(final Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration providedRuntime = configurations.getByName(WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME);
        Map<String, Map<String, Collection<Configuration>>> scopes = ideaModel.getModule().getScopes();
        scopes.get(GeneratedIdeaScope.PROVIDED.name()).get(IdeaDependenciesProvider.SCOPE_PLUS).add(providedRuntime);
        scopes.get(GeneratedIdeaScope.RUNTIME.name()).get(IdeaDependenciesProvider.SCOPE_MINUS).add(providedRuntime);
        scopes.get(GeneratedIdeaScope.TEST.name()).get(IdeaDependenciesProvider.SCOPE_MINUS).add(providedRuntime);
    }

    private static boolean includeModuleBytecodeLevelOverride(Project rootProject, JavaVersion moduleTargetBytecodeLevel) {
        if (!rootProject.getPlugins().hasPlugin(IdeaPlugin.class)) {
            return true;
        }

        IdeaProject ideaProject = ideaModelFor(rootProject).getProject();
        return !moduleTargetBytecodeLevel.equals(ideaProject.getTargetBytecodeVersion());
    }

    private static boolean includeModuleLanguageLevelOverride(Project rootProject, IdeaLanguageLevel moduleLanguageLevel) {
        if (!rootProject.getPlugins().hasPlugin(IdeaPlugin.class)) {
            return true;
        }

        IdeaProject ideaProject = ideaModelFor(rootProject).getProject();
        return !moduleLanguageLevel.equals(ideaProject.getLanguageLevel());
    }

}
