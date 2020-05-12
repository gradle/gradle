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
package org.gradle.plugins.ide.idea.model;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.plugins.ide.idea.model.internal.IdeaDependenciesProvider;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.resolver.DefaultGradleApiSourcesResolver;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.util.ConfigureUtil.configure;

/**
 * Enables fine-tuning module details (*.iml file) of the IDEA plugin.
 * <p>
 * Example of use with a blend of most possible properties.
 * Typically you don't have to configure this model directly because Gradle configures it for you.
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 *     id 'idea'
 * }
 *
 * //for the sake of this example, let's introduce a 'performanceTestCompile' configuration
 * configurations {
 *   performanceTestCompile
 *   performanceTestCompile.extendsFrom(testCompile)
 * }
 *
 * dependencies {
 *   //performanceTestCompile "some.interesting:dependency:1.0"
 * }
 *
 * idea {
 *
 *   //if you want parts of paths in resulting files (*.iml, etc.) to be replaced by variables (Files)
 *   pathVariables GRADLE_HOME: file('~/cool-software/gradle')
 *
 *   module {
 *     //if for some reason you want to add an extra sourceDirs
 *     sourceDirs += file('some-extra-source-folder')
 *
 *     //and some extra test source dirs
 *     testSourceDirs += file('some-extra-test-dir')
 *
 *     //and some extra resource dirs
 *     resourceDirs += file('some-extra-resource-dir')
 *
 *     //and some extra test resource dirs
 *     testResourceDirs += file('some-extra-test-resource-dir')
 *
 *     //and hint to mark some of existing source dirs as generated sources
 *     generatedSourceDirs += file('some-extra-source-folder')
 *
 *     //and some extra dirs that should be excluded by IDEA
 *     excludeDirs += file('some-extra-exclude-dir')
 *
 *     //if you don't like the name Gradle has chosen
 *     name = 'some-better-name'
 *
 *     //if you prefer different output folders
 *     inheritOutputDirs = false
 *     outputDir = file('muchBetterOutputDir')
 *     testOutputDir = file('muchBetterTestOutputDir')
 *
 *     //if you prefer different SDK than the one inherited from IDEA project
 *     jdkName = '1.6'
 *
 *     //put our custom test dependencies onto IDEA's TEST scope
 *     scopes.TEST.plus += [ configurations.performanceTestCompile ]
 *
 *     //if 'content root' (as IDEA calls it) of the module is different
 *     contentRoot = file('my-module-content-root')
 *
 *     //if you love browsing Javadoc
 *     downloadJavadoc = true
 *
 *     //and hate reading sources :)
 *     downloadSources = false
 *   }
 * }
 * </pre>
 *
 * For tackling edge cases, users can perform advanced configuration on the resulting XML file.
 * It is also possible to affect the way the IDEA plugin merges the existing configuration
 * via beforeMerged and whenMerged closures.
 * <p>
 * beforeMerged and whenMerged closures receive a {@link Module} parameter
 * <p>
 * Examples of advanced configuration:
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 *     id 'idea'
 * }
 *
 * idea {
 *   module {
 *     iml {
 *       //if you like to keep *.iml in a secret folder
 *       generateTo = file('secret-modules-folder')
 *
 *       //if you want to mess with the resulting XML in whatever way you fancy
 *       withXml {
 *         def node = it.asNode()
 *         node.appendNode('iLoveGradle', 'true')
 *         node.appendNode('butAlso', 'I find increasing pleasure tinkering with output *.iml contents. Yeah!!!')
 *       }
 *
 *       //closure executed after *.iml content is loaded from existing file
 *       //but before gradle build information is merged
 *       beforeMerged { module -&gt;
 *         //if you want skip merging exclude dirs
 *         module.excludeFolders.clear()
 *       }
 *
 *       //closure executed after *.iml content is loaded from existing file
 *       //and after gradle build information is merged
 *       whenMerged { module -&gt;
 *         //you can tinker with {@link Module}
 *       }
 *     }
 *   }
 * }
 *
 * </pre>
 */
public class IdeaModule {

    private String name;
    private Set<File> sourceDirs;
    private Set<File> generatedSourceDirs = Sets.newLinkedHashSet();
    private Set<File> resourceDirs = Sets.newLinkedHashSet();
    private Set<File> testResourceDirs = Sets.newLinkedHashSet();
    private Map<String, Map<String, Collection<Configuration>>> scopes = Maps.newLinkedHashMap();
    private boolean downloadSources = true;
    private boolean downloadJavadoc;
    private File contentRoot;
    private Set<File> testSourceDirs;
    private Set<File> excludeDirs;
    private Boolean inheritOutputDirs;
    private File outputDir;
    private File testOutputDir;
    private Map<String, File> pathVariables = Maps.newLinkedHashMap();
    private String jdkName;
    private IdeaLanguageLevel languageLevel;
    private JavaVersion targetBytecodeVersion;
    @SuppressWarnings("deprecation")
    private org.gradle.language.scala.ScalaPlatform scalaPlatform;
    private final IdeaModuleIml iml;
    private final Project project;
    private PathFactory pathFactory;
    private boolean offline;
    private Map<String, Iterable<File>> singleEntryLibraries;

    public IdeaModule(Project project, IdeaModuleIml iml) {
        this.project = project;
        this.iml = iml;
    }

    /**
     * Configures module name, that is the name of the *.iml file.
     * <p>
     * It's <b>optional</b> because the task should configure it correctly for you.
     * By default it will try to use the <b>project.name</b> or prefix it with a part of a <b>project.path</b> to make
     * sure the module name is unique in the scope of a multi-module build.
     * The 'uniqueness' of a module name is required for correct import into IDEA and the task will make sure the name
     * is unique.
     * <p>
     * <b>since</b> 1.0-milestone-2
     * <p>
     * If your project has problems with unique names it is recommended to always run <tt>gradle idea</tt> from the
     * root, i.e. for all subprojects.
     * If you run the generation of the IDEA module only for a single subproject then you may have different results
     * because the unique names are calculated based on IDEA modules that are involved in the specific build run.
     * <p>
     * If you update the module names then make sure you run <tt>gradle idea</tt> from the root, e.g. for all
     * subprojects, including generation of IDEA project.
     * The reason is that there may be subprojects that depend on the subproject with amended module name.
     * So you want them to be generated as well because the module dependencies need to refer to the amended project
     * name.
     * Basically, for non-trivial projects it is recommended to always run <tt>gradle idea</tt> from the root.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The directories containing the production sources.
     *
     * For example see docs for {@link IdeaModule}
     */
    public Set<File> getSourceDirs() {
        return sourceDirs;
    }

    public void setSourceDirs(Set<File> sourceDirs) {
        this.sourceDirs = sourceDirs;
    }

    /**
     * The directories containing the generated sources (both production and test sources).
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    public Set<File> getGeneratedSourceDirs() {
        return generatedSourceDirs;
    }

    public void setGeneratedSourceDirs(Set<File> generatedSourceDirs) {
        this.generatedSourceDirs = generatedSourceDirs;
    }

    /**
     * The keys of this map are the IDEA scopes. Each key points to another map that has two keys, plus and minus.
     * The values of those keys are collections of {@link org.gradle.api.artifacts.Configuration} objects. The files of the
     * plus configurations are added minus the files from the minus configurations. See example below...
     * <p>
     * Example how to use scopes property to enable 'performanceTestCompile' dependencies in the output *.iml file:
     * <pre class='autoTested'>
     * plugins {
     *     id 'java'
     *     id 'idea'
     * }
     *
     * configurations {
     *   performanceTestCompile
     *   performanceTestCompile.extendsFrom(testCompile)
     * }
     *
     * dependencies {
     *   //performanceTestCompile "some.interesting:dependency:1.0"
     * }
     *
     * idea {
     *   module {
     *     scopes.TEST.plus += [ configurations.performanceTestCompile ]
     *   }
     * }
     * </pre>
     */
    public Map<String, Map<String, Collection<Configuration>>> getScopes() {
        return scopes;
    }

    public void setScopes(Map<String, Map<String, Collection<Configuration>>> scopes) {
        this.scopes = scopes;
    }

    /**
     * Whether to download and add sources associated with the dependency jars. <p> For example see docs for {@link IdeaModule}
     */
    public boolean isDownloadSources() {
        return downloadSources;
    }

    public void setDownloadSources(boolean downloadSources) {
        this.downloadSources = downloadSources;
    }

    /**
     * Whether to download and add javadoc associated with the dependency jars. <p> For example see docs for {@link IdeaModule}
     */
    public boolean isDownloadJavadoc() {
        return downloadJavadoc;
    }

    public void setDownloadJavadoc(boolean downloadJavadoc) {
        this.downloadJavadoc = downloadJavadoc;
    }

    /**
     * The content root directory of the module. <p> For example see docs for {@link IdeaModule}
     */
    public File getContentRoot() {
        return contentRoot;
    }

    public void setContentRoot(File contentRoot) {
        this.contentRoot = contentRoot;
    }


    /**
     * The directories containing the test sources.
     *
     * For example see docs for {@link IdeaModule}
     */
    public Set<File> getTestSourceDirs() {
        return testSourceDirs;
    }

    public void setTestSourceDirs(Set<File> testSourceDirs) {
        this.testSourceDirs = testSourceDirs;
    }

    /**
     * The directories containing resources. <p> For example see docs for {@link IdeaModule}
     * @since 4.7
     */
    public Set<File> getResourceDirs() {
        return resourceDirs;
    }

    /**
     * Sets the directories containing resources. <p> For example see docs for {@link IdeaModule}
     * @since 4.7
     */
    public void setResourceDirs(Set<File> resourceDirs) {
        this.resourceDirs = resourceDirs;
    }

    /**
     * The directories containing the test resources. <p> For example see docs for {@link IdeaModule}
     * @since 4.7
     */
    public Set<File> getTestResourceDirs() {
        return testResourceDirs;
    }

    /**
     * Sets the directories containing the test resources. <p> For example see docs for {@link IdeaModule}
     * @since 4.7
     */
    public void setTestResourceDirs(Set<File> testResourceDirs) {
        this.testResourceDirs = testResourceDirs;
    }
    /**
     * Directories to be excluded. <p> For example see docs for {@link IdeaModule}
     */
    public Set<File> getExcludeDirs() {
        return excludeDirs;
    }

    public void setExcludeDirs(Set<File> excludeDirs) {
        this.excludeDirs = excludeDirs;
    }

    /**
     * If true, output directories for this module will be located below the output directory for the project;
     * otherwise, they will be set to the directories specified by {@link #getOutputDir()} and {@link #getTestOutputDir()}.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    public Boolean getInheritOutputDirs() {
        return inheritOutputDirs;
    }

    public void setInheritOutputDirs(Boolean inheritOutputDirs) {
        this.inheritOutputDirs = inheritOutputDirs;
    }

    /**
     * The output directory for production classes.
     * If {@code null}, no entry will be created.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * The output directory for test classes.
     * If {@code null}, no entry will be created.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    public File getTestOutputDir() {
        return testOutputDir;
    }

    public void setTestOutputDir(File testOutputDir) {
        this.testOutputDir = testOutputDir;
    }

    /**
     * The variables to be used for replacing absolute paths in the iml entries.
     * For example, you might add a {@code GRADLE_USER_HOME} variable to point to the Gradle user home dir.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    public Map<String, File> getPathVariables() {
        return pathVariables;
    }

    public void setPathVariables(Map<String, File> pathVariables) {
        this.pathVariables = pathVariables;
    }

    /**
     * The JDK to use for this module.
     * If {@code null}, the value of the existing or default ipr XML (inherited) is used.
     * If it is set to <code>inherited</code>, the project SDK is used.
     * Otherwise the SDK for the corresponding value of java version is used for this module.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    public String getJdkName() {
        return jdkName;
    }

    public void setJdkName(String jdkName) {
        this.jdkName = jdkName;
    }

    /**
     * The module specific language Level to use for this module.
     * When {@code null}, the module will inherit the language level from the idea project.
     * <p>
     * The Idea module language level is based on the {@code sourceCompatibility} settings for the associated Gradle project.
     */
    public IdeaLanguageLevel getLanguageLevel() {
        return languageLevel;
    }

    public void setLanguageLevel(IdeaLanguageLevel languageLevel) {
        this.languageLevel = languageLevel;
    }

    /**
     * The module specific bytecode version to use for this module.
     * When {@code null}, the module will inherit the bytecode version from the idea project.
     * <p>
     * The Idea module bytecode version is based on the {@code targetCompatibility} settings for the associated Gradle project.
     */
    public JavaVersion getTargetBytecodeVersion() {
        return targetBytecodeVersion;
    }

    public void setTargetBytecodeVersion(JavaVersion targetBytecodeVersion) {
        this.targetBytecodeVersion = targetBytecodeVersion;
    }

    /**
     * The Scala version used by this module.
     */
    @Deprecated
    public org.gradle.language.scala.ScalaPlatform getScalaPlatform() {
        return scalaPlatform;
    }

    @Deprecated
    public void setScalaPlatform(org.gradle.language.scala.ScalaPlatform scalaPlatform) {
        this.scalaPlatform = scalaPlatform;
    }

    /**
     * See {@link #iml(Action)}
     */
    public IdeaModuleIml getIml() {
        return iml;
    }

    /**
     * An owner of this IDEA module.
     * <p>
     * If IdeaModule requires some information from gradle this field should not be used for this purpose.
     * IdeaModule instances should be configured with all necessary information by the plugin or user.
     */
    public Project getProject() {
        return project;
    }

    public PathFactory getPathFactory() {
        return pathFactory;
    }

    public void setPathFactory(PathFactory pathFactory) {
        this.pathFactory = pathFactory;
    }

    /**
     * If true then external artifacts (e.g. those found in repositories) will not be included in the resulting classpath (only project and local file dependencies will be included).
     */
    public boolean isOffline() {
        return offline;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public Map<String, Iterable<File>> getSingleEntryLibraries() {
        return singleEntryLibraries;
    }

    public void setSingleEntryLibraries(Map<String, Iterable<File>> singleEntryLibraries) {
        this.singleEntryLibraries = singleEntryLibraries;
    }

    /**
     * Enables advanced configuration like tinkering with the output XML or affecting the way existing *.iml content is merged with gradle build information.
     * <p>
     * For example see docs for {@link IdeaModule}.
     */
    public void iml(Closure closure) {
        configure(closure, getIml());
    }

    /**
     * Enables advanced configuration like tinkering with the output XML or affecting the way existing *.iml content is merged with gradle build information.
     * <p>
     * For example see docs for {@link IdeaModule}.
     *
     * @since 3.5
     */
    public void iml(Action<? super IdeaModuleIml> action) {
        action.execute(iml);
    }

    /**
     * Configures output *.iml file.
     * It's <b>optional</b> because the task should configure it correctly for you (including making sure it is unique in the multi-module build).
     * If you really need to change the output file name (or the module name) it is much easier to do it via the <b>moduleName</b> property!
     * <p>
     * Please refer to documentation on <b>moduleName</b> property.
     * In IntelliJ IDEA the module name is the same as the name of the *.iml file.
     */
    public File getOutputFile() {
        return new File(iml.getGenerateTo(), getName() + ".iml");
    }

    public void setOutputFile(File newOutputFile) {
        setName(newOutputFile.getName().replaceFirst("\\.iml$", ""));
        getIml().setGenerateTo(newOutputFile.getParentFile());
    }

    /**
     * Resolves and returns the module's dependencies.
     *
     * @return dependencies
     */
    public Set<Dependency> resolveDependencies() {
        ProjectInternal projectInternal = (ProjectInternal) project;
        IdeArtifactRegistry ideArtifactRegistry = projectInternal.getServices().get(IdeArtifactRegistry.class);
        ProjectStateRegistry projectRegistry = projectInternal.getServices().get(ProjectStateRegistry.class);
        IdeaDependenciesProvider ideaDependenciesProvider = new IdeaDependenciesProvider(projectInternal, ideArtifactRegistry, projectRegistry, new DefaultGradleApiSourcesResolver(project));
        return ideaDependenciesProvider.provide(this);
    }

    @SuppressWarnings("unchecked")
    public void mergeXmlModule(Module xmlModule) {
        iml.getBeforeMerged().execute(xmlModule);

        Path contentRoot = getPathFactory().path(getContentRoot());
        Set<Path> sourceFolders = pathsOf(existing(getSourceDirs()));
        Set<Path> generatedSourceFolders = pathsOf(existing(getGeneratedSourceDirs()));
        Set<Path> testSourceFolders = pathsOf(existing(getTestSourceDirs()));
        Set<Path> resourceFolders = pathsOf(existing(getResourceDirs()));
        Set<Path> testResourceFolders = pathsOf(existing(getTestResourceDirs()));
        Set<Path> excludeFolders = pathsOf(getExcludeDirs());
        Path outputDir = getOutputDir() != null ? getPathFactory().path(getOutputDir()) : null;
        Path testOutputDir = getTestOutputDir() != null ? getPathFactory().path(getTestOutputDir()) : null;
        Set<Dependency> dependencies = resolveDependencies();
        String level = getLanguageLevel() != null ? getLanguageLevel().getLevel() : null;

        xmlModule.configure(
            contentRoot,
            sourceFolders, testSourceFolders,
            resourceFolders, testResourceFolders,
            generatedSourceFolders,
            excludeFolders,
            getInheritOutputDirs(), outputDir, testOutputDir,
            dependencies,
            getJdkName(), level
        );

        iml.getWhenMerged().execute(xmlModule);
    }

    private Set<File> existing(Set<File> files) {
        return Sets.filter(files, new Predicate<File>() {
            @Override
            public boolean apply(File file) {
                return file.exists();
            }
        });
    }

    private Set<Path> pathsOf(Set<File> files) {
        return files.stream().map(file -> getPathFactory().path(file)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

}
