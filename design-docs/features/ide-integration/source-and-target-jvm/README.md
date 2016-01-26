# Expose source and target platforms of JVM language projects

- [x] TAPI: Expose 'natures' and 'builders' for Eclipse projects
- [x] TAPI: Expose Java source level for Java projects to Eclipse
- [ ] TAPI: Expose Java source level for Java projects to IDEA
- [ ] TAPI: Expose target JDK for Java projects to Eclipse
- [ ] TAPI: Expose target JDK for Java projects to IDEA
- [ ] Expose Idea module specific source level in `IdeaPlugin`
- [ ] Expose Idea module specific bytecode level in `IdeaPlugin`
- [x] [Buildship: Consume additional builders and natures from the TAPI](https://github.com/eclipse/buildship/blob/master/docs/stories/ToolingAPI.md#set-additional-builders-and-natures-on-the-projects)
- [ ] [Buildship: Consume Java source level for Java projects from the TAPI](https://github.com/eclipse/buildship/blob/master/docs/stories/ToolingAPI.md#set-source-level-for-java-projects)
- [ ] [Buildship: Consume target JDK for Java projects from the TAPI](https://github.com/eclipse/buildship/blob/master/docs/stories/ToolingAPI.md#configure-the-target-jdk-for-java-projects)


## Stories

### Story - Expose natures and builders for projects

The Motiviation here is to model java projects better within the EclipseProject model. Therefore we want to provide
the Eclipse Model with information about natures and builders. A Java Project is identified
by having an EclipseModel providing a java nature. IDEs should not guess which natures and builders to add but get the information
from the TAPI.

##### The API

    interface EclipseProject {
        ...
        ...
        DomainObjectSet<? extends EclipseProjectNature> getProjectNatures();

        DomainObjectSet<? extends BuildCommand> getBuildCommands()
        ...
    }

    interface EclipseBuildCommand{
        String getName()
        Map<String,String> getArguments()
    }

    public interface EclipseProjectNature {

        /**
         * Returns the unique identifier of the project nature.
         */
        String getId();
    }

### Story - Expose Java source level for Java projects to Eclipse

The IDE models should provide the java source compatibility level. To model java language specific information
we want to have a dedicated model for eclipse specific java information and gradle specific java information.

##### The API

    interface JavaSourceSettings {
        JavaVersion getSourceLanguageLevel()
    }

    interface JavaSourceAware {
        JavaSourceSettings getJavaSourceSettings()
    }

    interface EclipseProject extends JavaSourceAware {
    }

- The `JavaSourceSettings` interface describes Java-specific details for a model.
  It initially defines only one attribute, describing the `sourceLanguageLevel`.
- The `getSourceLanguageLevel()` returns the `eclipse.jdt.sourceCompatibility` level that is configurable within the `build.gradle` or per default uses
similar version as `JavaConvention.sourceCompatibility` configuration.
- For a no Java Project `JavaSourceAware.getJavaSourceSettings()` returns null
- For older Gradle version the `JavaSourceAware.getJavaSourceSettings()` throws `UnsupportedMethodException`.

### Story - Expose Java source level for Java projects to IDEA

This is the IDEA-counterpart of the _Expose Java source level for Java projects to Eclipse_ story. The goal is to expose the source
language levels for each module in a project.

##### Estimate

- 2 days

##### The API

    interface IdeaModuleJavaSourceSettings extends JavaSourceSettings {
        boolean isSourceLanguageLevelInherited()
    }

    interface IdeaModule extends JavaSourceAware {
        IdeaModuleJavaSourceSettings getJavaSourceSettings()
    }

    interface IdeaProject extends JavaSourceAware {
        JavaSourceSettings getJavaSourceSettings()
    }

##### Implementation
- Introduce `IdeaModuleJavaSourceSettings` extending `JavaSourceSettings`.
- Let the `IdeaModule` model extend `JavaSourceAware` and expose specialized `IdeaModuleJavaSourceSettings`.
- Let the `IdeaProject` model extend `JavaSourceAware` to expose specialized `JavaSourceSettings`
- Update `DefaultIdeaProject` to implement new `getJavaSourceSettings()` method.
- Update `DefaultIdeaModule` to implement new `getJavaSourceSettings()` method.
- Update `IdeaModelBuilder` to set values for `getJavaSourceSettings()` for `IdeaProject` and `IdeaModule`
    - not a Java project, then `IdeaProject.getLanguageLevel()` should be returned
    - otherwise configure it as follows
        - `IdeaProject.getJavaSourceSettings().getSourceLanguageLevel()` is calculated from `org.gradle.plugins.ide.idea.model.IdeaProject.getLanguageLevel()`.
        - `IdeaModule.getJavaSourceSettings().getSourceLanguageLevel()` is calculated from `project.sourceCompatibility`
        - `IdeaModule.getSourceLanguageLevel().isInherited` returns `false` if different from the IDEA project, `true` if the same.
- Add a comment on `IdeaProject.getLanguageLevel()` that `getJavaSourceSettings()` should be preferred.
- For older Gradle versions value for `javaSourceSettings.sourceLanguageLevel`
    - Use an Action<SourceObjectMapping> to wire IdeaProject.languageLevel and the javaSourceSettings.sourceLanguageLevel.
        ( see `TaskPropertyHandlerFactory` as an example for this)
    - for IdeaModule, let the TAPI throw an `UnsupportedMethodException`

##### Test coverage

- `IdeaProject.languageLevel` always has the same value as `IdeaProject.javaSourceSettings.javaVersion`
- `IdeaModule.getJavaSourceSettings()` returns `languageLevel` for non java projects
- `IdeaModule.getJavaSourceSettings().isInherited()` returns true when the same as project's version, false when different.
- `IdeaProject.getJavaSourceSettings()` returns inferred value from `languageLevel` for older target Gradle version.
- `IdeaModule.getJavaSourceSettings()` throws UnsupportedMethodException for older target Gradle version.
- `IdeaProject.getJavaSourceSettings().getSourceLanguageLevel()` matches language level information obtained from `IdeaProject.getLanguageLevel()`.
- `IdeaModule.getJavaSourceSettings().getSourceLanguageLevel()` matches language level information obtained from `project.sourceCompatibility`.
- Can handle multi project builds with different source levels per subproject.
- Can handle multi project builds where some projects are not a Java project and some are.
- Can handle multi project builds where root project is not a Java project, but some of its children are.
- Can handle multi project builds where no projects are Java projects.

### Story - Expose target JDK for Java projects to Eclipse

##### Estimate
- 3 days


##### The API

    interface JavaRuntime {
        JavaVersion getJavaVersion()
        File getHomeDirectory()
    }

    interface EclipseJavaSourceSettings {
        JavaRuntime getTargetRuntime()
        JavaVersion getTargetBytecodeVersion()
    }

    interface EclipseProject extends JavaSourceAware {
        JavaSourceSettings getJavaSourceSettings()
    }


- A Java runtime can be used for projects that don't have any Java source. In eclipse this projects are is modelled as java projects.

##### Implementation

- ~~Add `JavaRuntime getTargetRuntime()` to `EclipseJavaSourceSettings`.~~
- ~~`JavaRuntime` should expose~~
    - ~~`JavaVersion getJavaVersion()` - the version of the JRE~~
    - ~~`File getHomeDirectory()` - the directory of the JRE in use~~
- ~~Add `JavaVersion getTargetBytecodeLevel()` to `EclipseJavaSourceSettings` to expose the java target combatibility level.~~
- ~~Update `DefaultJavaSourceSettings` to expose JRE and target language level information based on current JVM in use~~
- ~~Update `EclipseModelBuilder` to set values for the target language level and target runtime~~
    - ~~`EclipseJavaSourceSettings.getTargetBytecodeLevel()` returns the value of `eclipse.jdt.targetCompatibility` when `java-base` project is applied.~~
- ~~Update `.classpath` generation, so that tooling model and generated files are consistent.~~

##### Test coverage

- ~~`EclipseProject.getJavaSourceSettings().getTargetRuntime()` points to JVM in use~~
    - ~~`homeDirectory` pointing to the java home of the jvm in use~~
    - ~~`javaVersion` reflecting the java version of the jvm in use~~

- ~~Java project defining custom target compatibility via `project.targetCompatibility`~~
- ~~Java project, with 'eclipse' plugin defining eclipse specific target compatibility via `eclipse.jdt.targetCompatibility`~~
- ~~Multiproject java project build with different target levels per subproject.~~
- ~~Project that is not a Java project.~~
- ~~throws meaningful error for older Gradle provider versions when requesting EclipseProject.getJavaSourceSettings().getTargetRuntime()~~
- ~~throws meaningful error for older Gradle provider versions when requesting EclipseProject.getJavaSourceSettings().getTargetCompatibilityLevel()~~
- ~~custom java runtime name for eclipse classpath generation~~

### Story - Expose target JDK for Java projects to IDEA

##### the API

    interface IdeaModuleJavaSourceSettings extends JavaSourceSettings {
        boolean isTargetRuntimeInherited()
        boolean isTargetBytecodeLevelInherited()
    }

    interface IdeaProjectJavaSourceSettings extends JavaSourceSettings {
    }

    interface JavaSourceSettings {
        JavaRuntime getTargetRuntime()
        JavaVersion getTargetBytecodeLevel()
    }

##### Estimate

- 2 days

##### Implementation

- ~~move EclipseJavaSourceSettings.getTargetRuntime() into JavaSourceSettings~~
- ~~move EclipseJavaSourceSettings.getTargetBytecodeLevel() into JavaSourceSettings~~
- ~~for each module set `IdeaModuleJavaSourceSettings.targetRuntime` to current runtime in use and `IdeaModuleJavaSourceSettings.targetRuntimeInherited = true`~~
- ~~set `IdeaProjectJavaSourceSettings.targetRuntime` to current runtime~~
- ~~for each module set `IdeaModuleJavaSourceSettings.targetBytecodeLevel` to `JavaConvention.targetCompatibilityLevel`~~
- ~~set `IdeaProjectJavaSourceSettings.targetBytecodeLevel` to highest bytecode level found among the modules~~
- ~~set `IdeaModuleJavaSourceSettings.targetBytecodeLevelInherited = true` for modules with same target bytecode level as in `IdeaProjectJavaSourceSettings.targetBytecodeLevel`~~
- ~~for modules having same `targetBytecodeLevel` as `IdeaProjectJavaSourceSettings.targetBytecodeLevel` set `IdeaModuleJavaSourceSettings.targetBytecodeLevelInherited = true` ~~

##### Test cases

- ~~Multiproject build with same target Java versions~~
- ~~Multiproject build with mix of target Java versions~~
- ~~Multiproject build with mix of Java and non-Java projects~~
- ~~Multiproject build with no Java projects~~
- ~~meaningful error for older gradle providers~~

### Story - Expose Idea module specific source level in IdeaPlugin

##### Implementation

* set idea project language level to
** 1.6 if no idea module in the multiproject builds is a java project
** to the highest `sourceCompatibility` level found in any project with idea + javabase plugin applied.
* introduce read only `languageLevel` property in `org.gradle.plugins.ide.idea.model.IdeaModule`
* module specific language level derived from `project.sourceCompatibility`
** set module specific language level to null project is no Java project
** set module specific language level if root project does not apply idea plugin
** set module specific language level to null if `org.gradle.plugins.ide.idea.model.IdeaProject` is explicit set in root
** set module specific language level if `project.sourceCompatibility` differs from calculated root projects ``org.gradle.plugins.ide.idea.model.IdeaProject.getLanguageLevel()`
** set module specific language level to null if `project.sourceCompatibility` equals `org.gradle.plugins.ide.idea.model.IdeaProject.getLanguageLevel()`
* module specific language level != null is respected in generated `*.iml` file setting `LANGUAGE_LEVEL` attribute on NewModuleRootManager elemenet (e.g. `LANGUAGE_LEVEL="JDK_1_6"`)

##### Test coverage

- Multiproject build with mix of source compatibility level (subA `1.6`, subB `1.7` and subC `1.8`) and explicit configured `IdeaProject.languageLevel` (`1.7`)
    - `.ipr` file has `ProjectRootManager` component configured with `languageLevel="JDK_1_7"`.
    - subA `.iml` file has `LANGUAGE_LEVEL` explicitly set to `JDK_1_6`.
    - subB `.iml` file has no explicit `LANGUAGE_LEVEL` property set.
    - subC `.iml` file has `LANGUAGE_LEVEL` explicitly set to `JDK_1_8`.
- Multiproject build with mix of source compatibility level (subA `1.6`, subB `1.7` and subC `1.8`) and no explicit configured value for `IdeaProject.languageLevel`
    - `.ipr` file has `ProjectRootManager` component configured with `languageLevel="JDK_1_6"`.
    - subA `.iml` file has no explicit `LANGUAGE_LEVEL` property set.
    - subB `.iml` file has `LANGUAGE_LEVEL` explicitly set to `JDK_1_7`.
    - subC `.iml` file has `LANGUAGE_LEVEL` explicitly set to `JDK_1_8`.
- Multiproject build with mix of Java (subA `1.6`, subB `1.7`) and non-Java (subC) projects
    - `.ipr` file has `ProjectRootManager` component configured with `languageLevel="JDK_1_7"`.
    - non java projects have no `LANGUAGE_LEVEL` property set. (current behaviour)
    - subA `.iml` file has `LANGUAGE_LEVEL` explicitly set to `JDK_1_6`.

### Story - Expose Idea module specific bytecode level in IdeaPlugin

- if not specified otherwise, the idea project bytecode level is derived from the java runtime used.
- The used java runtime can already be configured in the idea plugin via `org.gradle.plugins.ide.idea.model.IdeaProject.jdkName`.
    - this implicitly changes the default bytecode level for an idea project.
- to set the bytecode level on project level explicitly (independent from the target runtime) `<bytecodeTargetLevel target="1.7" />` can be added to the CompilerConfiguration in the `*.ipr` file.
- to set the bytecode level on module level explicitly (independent from the target runtime and project bytecode level) `<bytecodeTargetLevel><module name="ModuleName" target="1.6" /></bytecodeTargetLevel>` can be set in the `*.ipr` file.

##### Implementation

- ~~if all java modules have same value for `project.targetCompatibility`~~
    - ~~when differs from `org.gradle.plugins.ide.idea.model.IdeaProject.jdkName` set project bytecode level explicitly in .ipr file~~
- ~~if java modules have different value for `project.targetCompatibility`~~
    -  ~~For any module where `project.targetCompatibility` doesn't match `IdeaProject.jdkName`, specify the 'bytecode level' for that module in the .ipr file.~~

##### Test coverage

- ~~Multiproject build with same target compatibility (`1.7`) and jdkName set to (`1.7`)~~
    - ~~`CompilerConfiguration` component in `.ipr` does not contain `bytecodeTargetLevel` entry.~~
- ~~Multiproject build with module target compatibility (`1.7`) and jdkName set to (`1.8`)~~
    - ~~`CompilerConfiguration` component in `.ipr` file has entry  `<bytecodeTargetLevel target="1.7" />`~~
- ~~Multiproject build with mix of different target compatibility levels (SubA `1.6`, SubB `1.7`, SubC `1.8`, SubD no java project and jdkName set to `1.8`)~~
    - ~~`CompilerConfiguration` component in `.ipr` does not contain default `bytecodeTargetLevel target`entry.~~
    - ~~`CompilerConfiguration` component in `.ipr` contains entry per module~~
        - ~~`<module name="SubA" target="1.6" />`~~
        - ~~`<module name="SubB" target="1.7" />`~~
    - ~~no `CompilerConfiguration` module entry for SubC~~
    - ~~no `CompilerConfiguration` module entry for SubD~~
