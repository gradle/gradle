## M8: Dependency substitution for Play projects in a composite build

### Overview

Spike: https://github.com/gradle/gradle/commit/95f3d9a0d7f9ad09caea364de1f3916b21628301

Need a minimal amount of support for developing a Play application with IntelliJ Community Edition. It's OK if we need to have a separate plugin, but ideally the extra configuration would automatically be applied when both `play` and `idea` are applied.

Need to support:
- Code editing/auto-complete for Scala/Java sources
- Running Play application from IntelliJ as a Run Configuration in continuous build

### API

No API changes. All of the existing IDEA related tasks and extensions are reused.

### Implementation notes

The IDEA plugin currently maps sourceSets and their outputs to an IdeaModule's sourceDirs, testSourceDirs and scopes. It also looks at the existing Scala (current model) plugin configuration to determine the version of Scala used.

When we create a `PlayApplicationBinary`, it can consist of Scala, Java, Routes and Twirl sources. Fortunately, there can only be a single Play component in a project and that component can only produce a single binary. We can map the sources from the PlayApplicationBinarySpec into `sourceDirs`. The Play plugin does not currently model test directories as TestSuites, so we will need to follow the same hardcoded conventions (sources in test/ and output in build/playBinary/testClasses).

The IDEA Scala code currently resolves all dependencies and then extracts the Scala runtime version from it to create a Scala SDK entry in the IDEA generated files. 

### Test coverage

- (existing coverage) When the Play plugin is not applied, nothing explodes.
- When applying the Play plugin and IDEA plugin, nothing explodes.
- When both Play and IDEA plugins are applied, we can generate IDEA files. Check:
    - IDEA file(s) contain path to sources
    - IDEA file(s) contain path to generated sources
    - IDEA file(s) contain corect version of Scala and a Scala compiler classpath
    - play dependencies are COMPILE scope, playTest dependencies are TEST scope and playRun dependencies are RUNTIME scope
- When Play configuration changes (e.g., targetPlatform), IDEA metadata is regenerated
- Can define custom source sets and they are included in IDEA metadata
- Can generate metadata for multi-project build with a mixture of Play and non-Play projects (basically the play/multiproject sample)
- Manual testing that the generated projects look "sane" in IntelliJ

### Documentation

- Need to update Play documentation to describe how to enable IDEA generation and any shortcomings.

### Open issues

- Problems identified in spike:
    - To properly generate the metadata, need access to Play model classes and existing IDEA classes.  Should we introduce a new ide-model subproject? (spike just adds a dependency between ide and platformPlay)
    - Scala compiler classpath should probably only contain Scala Compiler and not the full Play runtime.
        - SBT dumps everything onto the Scala classpath
        - We would need to expose ScalaRuntime as an extension to get the same behavior as before or expose ToolChain's classpath?
    - Since the routes/Twirl templates generate Scala/Java code, we need to include these paths as source inputs (so everything will compile in IntelliJ Community). The IDEA plugin adds buildDir as an exclude directory, which excludes our source files. Spike does not add buildDir as an exclude directory, but probably should.
        - Potentially we could add the compiled output directories (not the source directories) as dependency?
    - Should we create a `Play Run` configuration of our own (that runs `gradle -t runPlayBinary`)?
    - The spike ignores sourceCompatibility/targetCompatibility and sets the targetCompatibility/languageLevel based on the targetPlatform of the binary.
