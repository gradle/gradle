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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.JvmTestSuitePlugin;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.plugins.internal.JavaPluginHelper;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.idea.internal.IdeaModuleMetadata;
import org.gradle.plugins.ide.idea.internal.IdeaScalaConfigurer;
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
import org.gradle.plugins.ide.internal.configurer.UniqueProjectNameProvider;
import org.gradle.testing.base.TestingExtension;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private static final String IDEA_MODULE_TASK_NAME = "ideaModule";
    private static final String IDEA_PROJECT_TASK_NAME = "ideaProject";
    private static final String IDEA_WORKSPACE_TASK_NAME = "ideaWorkspace";

    private final Instantiator instantiator;
    private IdeaModel ideaModel;
    private List<Project> allJavaProjects;
    private final UniqueProjectNameProvider uniqueProjectNameProvider;
    private final IdeArtifactRegistry artifactRegistry;
    private final ProjectStateRegistry projectPathRegistry;

    @Inject
    public IdeaPlugin(Instantiator instantiator, UniqueProjectNameProvider uniqueProjectNameProvider, IdeArtifactRegistry artifactRegistry, ProjectStateRegistry projectPathRegistry) {
        this.instantiator = instantiator;
        this.uniqueProjectNameProvider = uniqueProjectNameProvider;
        this.artifactRegistry = artifactRegistry;
        this.projectPathRegistry = projectPathRegistry;
    }

    public IdeaModel getModel() {
        return ideaModel;
    }

    @Override
    protected String getLifecycleTaskName() {
        return "idea";
    }

    @Override
    protected void onApply(final Project project) {
        getLifecycleTask().configure(withDescription("Generates IDEA project files (IML, IPR, IWS)"));
        getCleanTask().configure(withDescription("Cleans IDEA project files (IML, IPR)"));

        ideaModel = project.getExtensions().create("idea", IdeaModel.class);

        configureIdeaWorkspace(project);
        configureIdeaProject(project);
        configureIdeaModule((ProjectInternal) project);
        configureForJavaPlugin(project);
        configureForWarPlugin(project);
        configureForScalaPlugin();
        configureForTestSuitesPlugin(project);
        linkCompositeBuildDependencies((ProjectInternal) project);
    }

    private void configureIdeaWorkspace(final Project project) {
        final IdeaWorkspace workspace = project.getObjects().newInstance(IdeaWorkspace.class);
        ideaModel.setWorkspace(workspace);

        if (isRoot()) {
            workspace.setIws(new XmlFileContentMerger(new XmlTransformer()));

            final TaskProvider<GenerateIdeaWorkspace> task = project.getTasks().register(IDEA_WORKSPACE_TASK_NAME, GenerateIdeaWorkspace.class, workspace);
            task.configure(new Action<GenerateIdeaWorkspace>() {
                @Override
                public void execute(GenerateIdeaWorkspace task) {
                    task.setDescription("Generates an IDEA workspace file (IWS)");
                    task.setOutputFile(new File(project.getProjectDir(), project.getName() + ".iws"));
                }
            });
            addWorker(task, IDEA_WORKSPACE_TASK_NAME, false);
        }
    }

    private void configureIdeaProject(final Project project) {
        if (isRoot()) {
            XmlFileContentMerger ipr = new XmlFileContentMerger(new XmlTransformer());
            final IdeaProject ideaProject = instantiator.newInstance(IdeaProject.class, project, ipr);
            final TaskProvider<GenerateIdeaProject> projectTask = project.getTasks().register(IDEA_PROJECT_TASK_NAME, GenerateIdeaProject.class, ideaProject);
            projectTask.configure(new Action<GenerateIdeaProject>() {
                @Override
                public void execute(GenerateIdeaProject projectTask) {
                    projectTask.setDescription("Generates IDEA project file (IPR)");
                }
            });
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
                    return new PathFactory().addPathVariable("PROJECT_DIR", projectTask.get().getOutputFile().getParentFile());
                }
            });

            addWorker(projectTask, IDEA_PROJECT_TASK_NAME);

            addWorkspace(ideaProject);
        }
    }

    private static IdeaModel ideaModelFor(Project project) {
        return project.getExtensions().getByType(IdeaModel.class);
    }

    private JavaVersion getMaxJavaModuleCompatibilityVersionFor(Function<Project, JavaVersion> toJavaVersion) {
        List<Project> allJavaProjects = getAllJavaProjects();
        if (allJavaProjects.isEmpty()) {
            return JavaVersion.VERSION_1_6;
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

    private void configureIdeaModule(final ProjectInternal project) {
        IdeaModuleIml iml = new IdeaModuleIml(new XmlTransformer(), project.getProjectDir());
        final IdeaModule module = instantiator.newInstance(IdeaModule.class, project, iml);

        final TaskProvider<GenerateIdeaModule> task = project.getTasks().register(IDEA_MODULE_TASK_NAME, GenerateIdeaModule.class, module);
        task.configure(new Action<GenerateIdeaModule>() {
            @Override
            public void execute(GenerateIdeaModule task) {
                task.setDescription("Generates IDEA module files (IML)");
            }
        });
        ideaModel.setModule(module);

        final String defaultModuleName = uniqueProjectNameProvider.getUniqueName(project);
        module.setName(defaultModuleName);

        ConventionMapping conventionMapping = ((IConventionAware) module).getConventionMapping();
        Set<File> sourceDirs = Sets.newLinkedHashSet();
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
        Set<File> testSourceDirs = Sets.newLinkedHashSet();
        conventionMapping.map("testSourceDirs", new Callable<Set<File>>() {
            @Override
            public Set<File> call() {
                return testSourceDirs;
            }
        });
        Set<File> resourceDirs = Sets.newLinkedHashSet();
        conventionMapping.map("resourceDirs", new Callable<Set<File>>() {
            @Override
            public Set<File> call() throws Exception {
                return resourceDirs;
            }
        });
        Set<File> testResourceDirs = Sets.newLinkedHashSet();
        conventionMapping.map("testResourceDirs", new Callable<Set<File>>() {
            @Override
            public Set<File> call() throws Exception {
                return testResourceDirs;
            }
        });
        Set<File> excludeDirs = Sets.newLinkedHashSet();
        conventionMapping.map("excludeDirs", new Callable<Set<File>>() {
            @Override
            public Set<File> call() {
                excludeDirs.add(project.file(".gradle"));
                excludeDirs.add(project.getBuildDir());
                return excludeDirs;
            }
        });

        conventionMapping.map("pathFactory", new Callable<PathFactory>() {
            @Override
            public PathFactory call() {
                final PathFactory factory = new PathFactory();
                factory.addPathVariable("MODULE_DIR", task.get().getOutputFile().getParentFile());
                for (Map.Entry<String, File> entry : module.getPathVariables().entrySet()) {
                    factory.addPathVariable(entry.getKey(), entry.getValue());
                }
                return factory;
            }

        });

        artifactRegistry.registerIdeProject(new IdeaModuleMetadata(module, task));

        addWorker(task, IDEA_MODULE_TASK_NAME);
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
        JvmSoftwareComponentInternal javaComponent = JavaPluginHelper.getJavaComponent(project);
        JvmTestSuite defaultTestSuite = JavaPluginHelper.getDefaultTestSuite(project);

        project.getTasks().withType(GenerateIdeaModule.class).configureEach(ideaModule -> {
            // Dependencies
            ideaModule.dependsOn((Callable<FileCollection>) () ->
                javaComponent.getMainOutput().getDirs().plus(defaultTestSuite.getSources().getOutput().getDirs())
            );
        });

        // Defaults
        setupScopes(javaComponent, defaultTestSuite);

        // Convention
        ConventionMapping convention = ((IConventionAware) ideaModel.getModule()).getConventionMapping();
        Set<File> sourceDirs = Sets.newLinkedHashSet();
        convention.map("sourceDirs", new Callable<Set<File>>() {
            @Override
            public Set<File> call() {
                SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
                sourceDirs.addAll(sourceSets.getByName("main").getAllJava().getSrcDirs());
                return sourceDirs;
            }
        });
        Set<File> resourceDirs = Sets.newLinkedHashSet();
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

    private void setupScopes(JvmSoftwareComponentInternal javaComponent, JvmTestSuite defaultTestSuite) {
        Map<String, Map<String, Collection<Configuration>>> scopes = Maps.newLinkedHashMap();
        for (GeneratedIdeaScope scope : GeneratedIdeaScope.values()) {
            Map<String, Collection<Configuration>> plusMinus = Maps.newLinkedHashMap();
            plusMinus.put(IdeaDependenciesProvider.SCOPE_PLUS, Lists.<Configuration>newArrayList());
            plusMinus.put(IdeaDependenciesProvider.SCOPE_MINUS, Lists.<Configuration>newArrayList());
            scopes.put(scope.name(), plusMinus);
        }

        Collection<Configuration> provided = scopes.get(GeneratedIdeaScope.PROVIDED.name()).get(IdeaDependenciesProvider.SCOPE_PLUS);
        provided.add(javaComponent.getCompileClasspathConfiguration());

        Collection<Configuration> runtime = scopes.get(GeneratedIdeaScope.RUNTIME.name()).get(IdeaDependenciesProvider.SCOPE_PLUS);
        runtime.add(javaComponent.getRuntimeClasspathConfiguration());

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
        project.getTasks().withType(GenerateIdeaModule.class).configureEach(new Action<GenerateIdeaModule>() {
            @Override
            public void execute(GenerateIdeaModule ideaModule) {
                ConfigurationContainer configurations = project.getConfigurations();
                Configuration providedRuntime = configurations.getByName(WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME);
                Collection<Configuration> providedPlus = ideaModule.getModule().getScopes().get(GeneratedIdeaScope.PROVIDED.name()).get(IdeaDependenciesProvider.SCOPE_PLUS);
                providedPlus.add(providedRuntime);
                Collection<Configuration> runtimeMinus = ideaModule.getModule().getScopes().get(GeneratedIdeaScope.RUNTIME.name()).get(IdeaDependenciesProvider.SCOPE_MINUS);
                runtimeMinus.add(providedRuntime);
                Collection<Configuration> testMinus = ideaModule.getModule().getScopes().get(GeneratedIdeaScope.TEST.name()).get(IdeaDependenciesProvider.SCOPE_MINUS);
                testMinus.add(providedRuntime);
            }
        });
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

    private void configureForScalaPlugin() {
        project.getPlugins().withType(ScalaBasePlugin.class, new Action<ScalaBasePlugin>() {
            @Override
            public void execute(ScalaBasePlugin scalaBasePlugin) {
                ideaModuleDependsOnRoot();
            }
        });
        if (isRoot()) {
            new IdeaScalaConfigurer(project).configure();
        }
    }

    private void ideaModuleDependsOnRoot() {
        // see IdeaScalaConfigurer which requires the ipr to be generated first
        project.getTasks().named(IDEA_MODULE_TASK_NAME, dependsOn(project.getRootProject().getTasks().named(IDEA_PROJECT_TASK_NAME)));
    }

    private void linkCompositeBuildDependencies(final ProjectInternal project) {
        if (isRoot()) {
            getLifecycleTask().configure(
                task -> task.dependsOn(
                    (TaskDependencyContainer) context -> visitAllImlArtifactsInComposite(project, ideaModel.getProject(), context)
                )
            );
        }
    }

    private void visitAllImlArtifactsInComposite(ProjectInternal project, IdeaProject ideaProject, TaskDependencyResolveContext context) {
        ProjectComponentIdentifier thisProjectId = projectPathRegistry.stateFor(project).getComponentIdentifier();
        for (IdeArtifactRegistry.Reference<IdeaModuleMetadata> reference : artifactRegistry.getIdeProjects(IdeaModuleMetadata.class)) {
            BuildIdentifier otherBuildId = reference.getOwningProject().getBuild();
            if (thisProjectId.getBuild().equals(otherBuildId)) {
                // IDEA Module for project in current build: don't include any module that has been excluded from project
                boolean found = false;
                for (IdeaModule ideaModule : ideaProject.getModules()) {
                    if (reference.get().getFile().equals(ideaModule.getOutputFile())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    continue;
                }
            }
            reference.visitDependencies(context);
        }
    }
}
