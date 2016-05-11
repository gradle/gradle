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
package org.gradle.plugins.ide.idea;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Callables;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.language.scala.plugins.ScalaLanguagePlugin;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.api.XmlGeneratorTask;
import org.gradle.plugins.ide.idea.internal.IdeaNameDeduper;
import org.gradle.plugins.ide.idea.internal.IdeaScalaConfigurer;
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaModuleIml;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.gradle.plugins.ide.idea.model.IdeaWorkspace;
import org.gradle.plugins.ide.idea.model.PathFactory;
import org.gradle.plugins.ide.internal.IdePlugin;

import javax.inject.Inject;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Adds a GenerateIdeaModule task. When applied to a root project, also adds a GenerateIdeaProject task.
 * For projects that have the Java plugin applied, the tasks receive additional Java-specific configuration.
 */
public class IdeaPlugin extends IdePlugin {

    private final Instantiator instantiator;
    private IdeaModel ideaModel;

    @Inject
    public IdeaPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public IdeaModel getModel() {
        return ideaModel;
    }

    @Override
    protected String getLifecycleTaskName() {
        return "idea";
    }

    @Override
    protected void onApply(Project project) {
        getLifecycleTask().setDescription("Generates IDEA project files (IML, IPR, IWS)");
        getCleanTask().setDescription("Cleans IDEA project files (IML, IPR)");

        ideaModel = project.getExtensions().create("idea", IdeaModel.class);

        configureIdeaWorkspace(project);
        configureIdeaProject(project);
        configureIdeaModule(project);
        configureForJavaPlugin(project);
        configureForScalaPlugin();
        hookDeduplicationToTheRoot(project);
    }

    private void configureIdeaWorkspace(final Project project) {
        if (isRoot(project)) {
            Task task = project.getTasks().create("ideaWorkspace", GenerateIdeaWorkspace.class, new Action<GenerateIdeaWorkspace>() {
                @Override
                public void execute(GenerateIdeaWorkspace task) {
                    task.setDescription("Generates an IDEA workspace file (IWS)");
                    task.setWorkspace(new IdeaWorkspace());
                    task.getWorkspace().setIws(new XmlFileContentMerger(getTaskXmlTransformer(task)));
                    ideaModel.setWorkspace(task.getWorkspace());
                    task.setOutputFile(new File(project.getProjectDir(), project.getName() + ".iws"));
                }
            });
            addWorker(task, false);
        }
    }

    private void configureIdeaProject(final Project project) {
        if (isRoot(project)) {
            Task task = project.getTasks().create("ideaProject", GenerateIdeaProject.class, new Action<GenerateIdeaProject>() {
                @Override
                public void execute(final GenerateIdeaProject task) {
                    task.setDescription("Generates IDEA project file (IPR)");

                    XmlFileContentMerger ipr = new XmlFileContentMerger(getTaskXmlTransformer(task));
                    IdeaProject ideaProject = instantiator.newInstance(IdeaProject.class, project, ipr);
                    task.setIdeaProject(ideaProject);
                    ideaModel.setProject(ideaProject);

                    ideaProject.setOutputFile(new File(project.getProjectDir(), project.getName() + ".ipr"));
                    ConventionMapping conventionMapping = ((IConventionAware) ideaProject).getConventionMapping();
                    conventionMapping.map("jdkName", Callables.returning(JavaVersion.current().toString()));
                    conventionMapping.map("languageLevel", new Callable<IdeaLanguageLevel>() {
                        @Override
                        public IdeaLanguageLevel call() {
                            JavaVersion maxSourceCompatibility = getMaxJavaModuleCompatibilityVersionFor(project, new Function<Project, JavaVersion>() {
                                @Override
                                public JavaVersion apply(Project p) {
                                    return p.getConvention().getPlugin(JavaPluginConvention.class).getSourceCompatibility();
                                }
                            });
                            return new IdeaLanguageLevel(maxSourceCompatibility);
                        }
                    });
                    conventionMapping.map("targetBytecodeVersion", new Callable<JavaVersion>() {
                        @Override
                        public JavaVersion call() {
                            return getMaxJavaModuleCompatibilityVersionFor(project, new Function<Project, JavaVersion>() {
                                @Override
                                public JavaVersion apply(Project p) {
                                    return p.getConvention().getPlugin(JavaPluginConvention.class).getTargetCompatibility();
                                }
                            });
                        }
                    });
                    ideaProject.setWildcards(Sets.newLinkedHashSet(Arrays.asList("!?*.class", "!?*.scala", "!?*.groovy", "!?*.java")));
                    conventionMapping.map("modules", new Callable<List<IdeaModule>>() {
                        @Override
                        public List<IdeaModule> call() {
                            Iterable<Project> ideaProjects = Iterables.filter(project.getRootProject().getAllprojects(), new Predicate<Project>() {
                                @Override
                                public boolean apply(Project p) {
                                    return p.getPlugins().hasPlugin(IdeaPlugin.class);
                                }
                            });
                            return Lists.newArrayList(Iterables.transform(ideaProjects, new Function<Project, IdeaModule>() {
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
                            return new PathFactory().addPathVariable("PROJECT_DIR", task.getOutputFile().getParentFile());
                        }
                    });
                }
            });
            addWorker(task);
        }
    }

    private static JavaVersion getMaxJavaModuleCompatibilityVersionFor(Project project, Function<Project, JavaVersion> getJavaVersion) {
        Iterable<Project> javaProjects = Iterables.filter(project.getRootProject().getAllprojects(), new Predicate<Project>() {
            @Override
            public boolean apply(Project p) {
                return p.getPlugins().hasPlugin(IdeaPlugin.class) && p.getPlugins().hasPlugin(JavaBasePlugin.class);
            }
        });
        Iterable<JavaVersion> javaVersions = Iterables.transform(javaProjects, getJavaVersion);
        return Iterables.getLast(Sets.newTreeSet(javaVersions), JavaVersion.VERSION_1_6);
    }

    private void configureIdeaModule(final Project project) {
        Task task = project.getTasks().create("ideaModule", GenerateIdeaModule.class, new Action<GenerateIdeaModule>() {
            @Override
            public void execute(final GenerateIdeaModule task) {
                task.setDescription("Generates IDEA module files (IML)");
                IdeaModuleIml iml = new IdeaModuleIml(getTaskXmlTransformer(task), project.getProjectDir());
                final IdeaModule module = instantiator.newInstance(IdeaModule.class, project, iml);
                task.setModule(module);

                ideaModel.setModule(module);
                ConventionMapping conventionMapping = ((IConventionAware) module).getConventionMapping();
                conventionMapping.map("sourceDirs", Callables.returning(Sets.newLinkedHashSet()));
                conventionMapping.map("name", new Callable<String>() {
                    @Override
                    public String call() {
                        return project.getName();
                    }
                });
                conventionMapping.map("contentRoot", new Callable<File>() {
                    @Override
                    public File call() {
                        return project.getProjectDir();
                    }
                });
                conventionMapping.map("testSourceDirs", Callables.returning(Sets.newLinkedHashSet()));
                conventionMapping.map("excludeDirs", new Callable<Set<File>>() {
                    @Override
                    public Set<File> call() {
                        return Sets.newLinkedHashSet(Arrays.asList(project.file(".gradle"), project.getBuildDir()));
                    }
                });
                conventionMapping.map("pathFactory", new Callable<PathFactory>() {
                    @Override
                    public PathFactory call() {
                        PathFactory factory = new PathFactory();
                        factory.addPathVariable("MODULE_DIR", task.getOutputFile().getParentFile());
                        for (Map.Entry<String, File> pathVariable : module.getPathVariables().entrySet()) {
                            factory.addPathVariable(pathVariable.getKey(), pathVariable.getValue());
                        }
                        return factory;
                    }
                });
            }
        });
        addWorker(task);
    }

    private DomainObjectCollection<JavaPlugin> configureForJavaPlugin(final Project project) {
        return project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
            @Override
            public void execute(JavaPlugin javaPlugin) {
                configureIdeaModuleForJava(project);
            }
        });
    }

    private DomainObjectCollection<GenerateIdeaModule> configureIdeaModuleForJava(final Project project) {
        return project.getTasks().withType(GenerateIdeaModule.class, new Action<GenerateIdeaModule>() {
            @Override
            public void execute(GenerateIdeaModule ideaModule) {
                // Defaults
                ideaModule.getModule().setScopes(Maps.newHashMap(ImmutableMap.<String, Map<String, Collection<Configuration>>>builder()
                    .put("PROVIDED", newScopeDefaults())
                    .put("COMPILE", newScopeDefaults())
                    .put("RUNTIME", newScopeDefaults())
                    .put("TEST", newScopeDefaults())
                    .build()));
                // Convention
                ConventionMapping module = ((IConventionAware) ideaModule.getModule()).getConventionMapping();
                module.map("sourceDirs", new Callable<Set<File>>() {
                    @Override
                    public Set<File> call() {
                        SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
                        return sourceSets.getByName("main").getAllSource().getSrcDirs();
                    }
                });
                module.map("testSourceDirs", new Callable<Set<File>>() {
                    @Override
                    public Set<File> call() {
                        SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
                        return sourceSets.getByName("test").getAllSource().getSrcDirs();
                    }
                });
                module.map("singleEntryLibraries", new Callable<Map<String, Iterable<File>>>() {
                    @Override
                    public Map<String, Iterable<File>> call() {
                        SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
                        return Maps.newHashMap(ImmutableMap.<String, Iterable<File>>builder()
                            .put("RUNTIME", sourceSets.getByName("main").getOutput().getDirs())
                            .put("TEST", sourceSets.getByName("test").getOutput().getDirs())
                            .build());
                    }
                });
                module.map("targetBytecodeVersion", new Callable<JavaVersion>() {
                    @Override
                    public JavaVersion call() {
                        JavaVersion moduleTargetBytecodeLevel = project.getConvention().getPlugin(JavaPluginConvention.class).getTargetCompatibility();
                        return includeModuleBytecodeLevelOverride(project.getRootProject(), moduleTargetBytecodeLevel) ? moduleTargetBytecodeLevel : null;
                    }
                });
                module.map("languageLevel", new Callable<IdeaLanguageLevel>() {
                    @Override
                    public IdeaLanguageLevel call() {
                        IdeaLanguageLevel moduleLanguageLevel = new IdeaLanguageLevel(project.getConvention().getPlugin(JavaPluginConvention.class).getSourceCompatibility());
                        return includeModuleLanguageLevelOverride(project.getRootProject(), moduleLanguageLevel) ? moduleLanguageLevel : null;
                    }
                });
                // Dependencies
                ideaModule.dependsOn(new Callable<FileCollection>() {
                    @Override
                    public FileCollection call() {
                        SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
                        return sourceSets.getByName("main").getOutput().getDirs().plus(sourceSets.getByName("test").getOutput().getDirs());
                    }
                });
            }
        });
    }

    private static Map<String, Collection<Configuration>> newScopeDefaults() {
        return Maps.newHashMap(ImmutableMap.<String, Collection<Configuration>>of(
            "plus", Lists.<Configuration>newArrayList(),
            "minus", Lists.<Configuration>newArrayList()));
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
        project.getPlugins().withType(ScalaLanguagePlugin.class, new Action<ScalaLanguagePlugin>() {
            @Override
            public void execute(ScalaLanguagePlugin scalaLanguagePlugin) {
                ideaModuleDependsOnRoot();
            }
        });
        if (isRoot(project)) {
            new IdeaScalaConfigurer(project).configure();
        }
    }

    private void ideaModuleDependsOnRoot() {
        // see IdeaScalaConfigurer which requires the ipr to be generated first
        project.getTasks().findByName("ideaModule").dependsOn(project.getRootProject().getTasks().findByName("ideaProject"));
    }

    private void hookDeduplicationToTheRoot(Project project) {
        if (isRoot(project)) {
            project.getGradle().projectsEvaluated(new Closure(this, this) {
                @SuppressWarnings("unused")
                public void doCall() {
                    makeSureModuleNamesAreUnique();
                }
            });
        }
    }

    public void makeSureModuleNamesAreUnique() {
        new IdeaNameDeduper().configureRoot(project.getRootProject());
    }

    private static boolean isRoot(Project project) {
        return project.getParent() == null;
    }

    private static IdeaModel ideaModelFor(Project project) {
        return project.getExtensions().getByType(IdeaModel.class);
    }

    // TODO Move up and remove duplicate in EclipsePlugin
    protected static XmlTransformer getTaskXmlTransformer(XmlGeneratorTask task) {
        Method method = null;
        try {
            method = task.getClass().getMethod("getXmlTransformer");
            method.setAccessible(true);
            return (XmlTransformer) method.invoke(task);
        } catch (Exception e) {
            throw new UncheckedException(e);
        } finally {
            if (method != null) {
                try {
                    method.setAccessible(false);
                } catch (SecurityException ignore) {
                    // Ignore
                }
            }
        }
    }
}
