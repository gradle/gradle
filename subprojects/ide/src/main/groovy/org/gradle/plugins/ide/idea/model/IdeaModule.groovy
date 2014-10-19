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

package org.gradle.plugins.ide.idea.model

import org.gradle.api.Incubating
import org.gradle.api.artifacts.Configuration
import org.gradle.api.dsl.ConventionProperty
import org.gradle.plugins.ide.idea.model.internal.IdeaDependenciesProvider
import org.gradle.util.ConfigureUtil

/**
 * Enables fine-tuning module details (*.iml file) of the IDEA plugin .
 * <p>
 * Example of use with a blend of all possible properties.
 * Typically you don't have configure this model directly because Gradle configures it for you.
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'idea'
 *
 * //for the sake of this example, let's introduce a 'provided' configuration
 * configurations {
 *   provided
 *   provided.extendsFrom(compile)
 * }
 *
 * dependencies {
 *   //provided "some.interesting:dependency:1.0"
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
 *     //if you prefer different SDK than that inherited from IDEA project
 *     jdkName = '1.6'
 *
 *     //if you need to put 'provided' dependencies on the classpath
 *     scopes.PROVIDED.plus += [ configurations.provided ]
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
 * For tackling edge cases users can perform advanced configuration on resulting XML file.
 * It is also possible to affect the way the IDEA plugin merges the existing configuration
 * via beforeMerged and whenMerged closures.
 * <p>
 * beforeMerged and whenMerged closures receive {@link Module} object
 * <p>
 * Examples of advanced configuration:
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'idea'
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
 *       beforeMerged { module ->
 *         //if you want skip merging exclude dirs
 *         module.excludeFolders.clear()
 *       }
 *
 *       //closure executed after *.iml content is loaded from existing file
 *       //and after gradle build information is merged
 *       whenMerged { module ->
 *         //you can tinker with {@link Module}
 *       }
 *     }
 *   }
 * }
 *
 * </pre>
 */
class IdeaModule {

   /**
     * Configures module name, that is the name of the *.iml file.
     * <p>
     * It's <b>optional</b> because the task should configure it correctly for you.
     * By default it will try to use the <b>project.name</b> or prefix it with a part of a <b>project.path</b>
     * to make sure the module name is unique in the scope of a multi-module build.
     * The 'uniqueness' of a module name is required for correct import
     * into IDEA and the task will make sure the name is unique.
     * <p>
     * <b>since</b> 1.0-milestone-2
     * <p>
     * If your project has problems with unique names it is recommended to always run <tt>gradle idea</tt> from the root, i.e. for all subprojects.
     * If you run the generation of the IDEA module only for a single subproject then you may have different results
     * because the unique names are calculated based on IDEA modules that are involved in the specific build run.
     * <p>
     * If you update the module names then make sure you run <tt>gradle idea</tt> from the root, e.g. for all subprojects, including generation of IDEA project.
     * The reason is that there may be subprojects that depend on the subproject with amended module name.
     * So you want them to be generated as well because the module dependencies need to refer to the amended project name.
     * Basically, for non-trivial projects it is recommended to always run <tt>gradle idea</tt> from the root.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    String name

    /**
     * The directories containing the production sources.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    Set<File> sourceDirs

    /**
     * The directories containing the generated sources (both production and test sources).
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    @Incubating
    Set<File> generatedSourceDirs = []

    /**
     * The keys of this map are the IDEA scopes. Each key points to another map that has two keys, plus and minus.
     * The values of those keys are collections of {@link org.gradle.api.artifacts.Configuration} objects. The files of the
     * plus configurations are added minus the files from the minus configurations. See example below...
     * <p>
     * Example how to use scopes property to enable 'provided' dependencies in the output *.iml file:
     * <pre autoTested=''>
     * apply plugin: 'java'
     * apply plugin: 'idea'
     *
     * configurations {
     *   provided
     *   provided.extendsFrom(compile)
     * }
     *
     * dependencies {
     *   //provided "some.interesting:dependency:1.0"
     * }
     *
     * idea {
     *   module {
     *     scopes.PROVIDED.plus += [ configurations.provided ]
     *   }
     * }
     * </pre>
     */
    Map<String, Map<String, Collection<Configuration>>> scopes = [:]

    /**
     * Whether to download and add sources associated with the dependency jars.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    boolean downloadSources = true

    /**
     * Whether to download and add javadoc associated with the dependency jars.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    boolean downloadJavadoc = false

    /**
     * The content root directory of the module.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    File contentRoot

    /**
     * The directories containing the test sources.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    Set<File> testSourceDirs

    /**
     * {@link ConventionProperty} for the directories to be excluded.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    Set<File> excludeDirs

    /**
     * If true, output directories for this module will be located below the output directory for the project;
     * otherwise, they will be set to the directories specified by #outputDir and #testOutputDir.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    Boolean inheritOutputDirs

    /**
     * The output directory for production classes. If {@code null}, no entry will be created.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    File outputDir

    /**
     * The output directory for test classes. If {@code null}, no entry will be created.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    File testOutputDir

    /**
     * The variables to be used for replacing absolute paths in the iml entries. For example, you might add a
     * {@code GRADLE_USER_HOME} variable to point to the Gradle user home dir.
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    Map<String, File> pathVariables = [:]

    /**
     * The JDK to use for this module. If {@code null}, the value of the existing or default ipr XML (inherited)
     * is used. If it is set to <code>inherited</code>, the project SDK is used. Otherwise the SDK for the corresponding
     * value of java version is used for this module
     * <p>
     * For example see docs for {@link IdeaModule}
     */
    String jdkName

    /**
     * See {@link #iml(Closure) }
     */
    final IdeaModuleIml iml

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing *.iml content is merged with gradle build information.
     * <p>
     * For example see docs for {@link IdeaModule}.
     */
    void iml(Closure closure) {
        ConfigureUtil.configure(closure, getIml())
    }

    /**
     * Configures output *.iml file. It's <b>optional</b> because the task should configure it correctly for you
     * (including making sure it is unique in the multi-module build).
     * If you really need to change the output file name (or the module name) it is much easier to do it via the <b>moduleName</b> property!
     * <p>
     * Please refer to documentation on <b>moduleName</b> property. In IntelliJ IDEA the module name is the same as the name of the *.iml file.
     */
    File getOutputFile() {
        new File((File) iml.getGenerateTo(), getName() + ".iml")
    }

    void setOutputFile(File newOutputFile) {
        setName(newOutputFile.name.replaceFirst(/\.iml$/,""))
        iml.generateTo = newOutputFile.parentFile
    }

    /**
     * Resolves and returns the module's dependencies.
     *
     * @return dependencies
     */
    Set<Dependency> resolveDependencies() {
        return new IdeaDependenciesProvider().provide(this)
    }

    /**
     * An owner of this IDEA module.
     * <p>
     * If IdeaModule requires some information from gradle this field should not be used for this purpose.
     * IdeaModule instances should be configured with all necessary information by the plugin or user.
     */
    final org.gradle.api.Project project

    PathFactory pathFactory

    /**
     * If true then external artifacts (e.g. those found in repositories) will not be included in the resulting classpath
     * (only project and local file dependencies will be included).
     */
    boolean offline

    Map<String, Iterable<File>> singleEntryLibraries

    IdeaModule(org.gradle.api.Project project, IdeaModuleIml iml) {
        this.project = project
        this.iml = iml
    }

    void mergeXmlModule(Module xmlModule) {
        iml.beforeMerged.execute(xmlModule)

        def path = { getPathFactory().path(it) }
        def contentRoot = path(getContentRoot())
        Set sourceFolders = getSourceDirs().findAll { it.exists() }.collect { path(it) }
        Set generatedSourceFolders = getGeneratedSourceDirs().findAll { it.exists() }.collect { path(it) }
        Set testSourceFolders = getTestSourceDirs().findAll { it.exists() }.collect { path(it) }
        Set excludeFolders = getExcludeDirs().collect { path(it) }
        def outputDir = getOutputDir() ? path(getOutputDir()) : null
        def testOutputDir = getTestOutputDir() ? path(getTestOutputDir()) : null
        Set dependencies = resolveDependencies()

        xmlModule.configure(contentRoot, sourceFolders, testSourceFolders, generatedSourceFolders, excludeFolders,
                getInheritOutputDirs(), outputDir, testOutputDir, dependencies, getJdkName())

        iml.whenMerged.execute(xmlModule)
    }
}
