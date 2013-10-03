## New and noteworthy

Here are the new features introduced in this Gradle release.

### Faster builds with the daemon

TODO SF: mention the task artifact in-memory caching and memory impact

### Faster parallel builds with forked java compiler

Java code can be compiled in a [forked process](dsl/org.gradle.api.tasks.compile.JavaCompile.html).
This is generally safer and allows fine grained control of the jvm options of the compilation process.
It will be the default in some future version.
Gradle 1.9 brings improvements to the parallel build that use forked java compilation.
A separate compiler daemon process can now be forked per each parallel worker thread.
Basically it means that parallel builds that compile java code in a forked process may get faster.

### Improvements to support for building native binaries from C/C++/Assembler (i)

<!-- TODO:DAZ Flesh these out -->

* New 'assembler' and 'c' plugins to provide separate language support.
    * The 'cpp' plugin now only provides support for C++ sources.
    * Separately apply the 'assembler' or 'c' plugins for additional language support.

* Source set for component (executable or library) is automatically created.

<table>
    <tr><th>Gradle 1.8</th><th>Gradle 1.9</th></tr>
    <tr>
    <td>
<pre>apply plugin: 'cpp'
sources {
    main {
        cpp {}
    }
}
executables {
    main {
        source sources.main.cpp
    }
}</pre>
    </td>
    <td>
<pre>apply plugin: 'cpp'
executables {
    main {}
}</pre>
    </td>
    </tr>
</table>

* Replaced `compilerArgs`, `assemblerArgs` and `linkerArgs` with language-specific extensions.
    * Note that the language-specific element is only present if the appropriate plugin has been applied.
    * Linker arguments are no longer automatically escaped with '-Xlinker' on GCC.

<table>
    <tr><th>Gradle 1.8</th><th>Gradle 1.9</th></tr>
    <tr>
    <td>compilerArgs "-W"</td>
    <td>cppCompiler.args "-W"</td>
    </tr>
    <tr>
    <td>compilerArgs "-W"</td>
    <td>cCompiler.args "-W"</td>
    </tr>
    <tr>
    <td>assemblerArgs "-arch", "i386"</td>
    <td>assembler.args "-arch", "i386"</td>
    </tr>
    <tr>
    <td>linkerArgs "-no_pie"</td>
    <td>linker.args "-Xlinker", "-no_pie"</td>
    </tr>
    <tr>
    <td>staticLibArgs "-v"</td>
    <td>staticLibArchiver.args "-v"</td>
    </tr>
</table>

// TODO:DAZ Expand on these
* Define multiple tool chains to build native binary variants
    * This means you can build for multiple versions of the same tool chain.
* Build binary variants for different custom flavors
* Build binary variants for different target architectures
* Discovers Visual studio and Windows SDK installations, which means you can compile using the Visual C++ tools from the cygwin command prompt.
* Support for the [Clang](http://clang.llvm.org) compiler.

### FindBugs plugin provides new reporting capabilities

* If the plugin is configured to produce an XML report, the output can be augmented with human-readable messages. The follow example demonstrates its use:

<pre>
findbugsMain.reports {
    xml.enabled true
    xml.withMessages true
}
</pre>

* Additionally, the plugin allows for generating text and Emacs report formats.

<pre>
findbugsMain.reports {
    xml.enabled true
    text.enabled true
    emacs.enabled true
}
</pre>

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Renamings in incubating BuildSetup plugin

* The ´BuildSetup´ task was renamed to ´InitBuild´.
* The plugin ´build-setup was renamed to ´build-init´.
* The task ´setupBuild´ provided by the auto-applied BuildInit plugin was renamed to ´init´.
* The package name for the ´build-init´ related classes has changed from ´org.gradle.buildsetup´ to ´org.gradle.buildinit´.

### Changes to incubating Native Binary support

* The 'cpp' plugin no longer automatically adds support for C and Assembler sources.
* Replaced `compilerArgs` and `linkerArgs` with `cppCompiler.args` and `linker.args`.
* Renamed and restructure package organisation for domain, plugin and task classes. If you are referencing
  these classes directly you may need to update your build script for this reorganisation.
* The temporary file generated for compiler/linker input has been renamed from "compiler-options.txt" to "options.txt".
    * This file now only contains file inputs to the tool - all other options are supplied directly via the command line.
* Object files generated from the assembly of Assembler sources are no longer named '<file>.s.o'.
* Renamed method: BuildableModelElement.dependsOn() -> BuildableModelElement.builtBy()
* The `gpp-compiler` plugin was renamed to `gcc`. Class name was change to `GccCompilerPlugin`.
* Linker arguments are no longer automatically escaped with '-Xlinker' on GCC.
* The conventional source directories eg: `src/main/cpp` and `src/main/headers` are only applied if no source directories are explicitly
  configured. If you wish to define custom source locations, you must define _all_ of the source locations.


## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [John Engelman](https://github.com/johnrengelman)
    - Existence of pom file requires that declared artifacts can be found in the same repository (GRADLE-2034).
    - Fix publishing to Maven Local to follow Maven rules (GRADLE-2762).
* [Valdis Rigdon](https://github.com/valdisrigdon) - Adds the ability to specify xml:withMessages, text, or emacs for the FindBugs report.
* [Robert Kühne](https://github.com/sponiro)
    - Documentation fixes.
    - Fix a regression when adding Action instances to tasks (GRADLE-2774).
* [Mark Petrovic](https://github.com/ae6rt) - Cucumber test report file formation is failing (GRADLE-2739).

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
