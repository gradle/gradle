
This release of Gradle is mostly a bug fix release. However, it still contains some nice new features.

Faster incremental builds and parallel compilation.

Variants for native binaries.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Faster builds with the daemon

TODO SF: mention the task artifact in-memory caching and memory impact

### Faster parallel builds when using forked Java compilation

Gradle has long supported using an external Java process to compile Java code
(see the [JavaCompile](dsl/org.gradle.api.tasks.compile.JavaCompile.html) task for configuration details).
In previous versions of Gradle, forked Java compilation was a performance bottleneck when
[building in parallel](userguide/multi_project_builds.html#sec:parallel_execution).
As of Gradle 1.9, extra compilation processes will be created as needed when building in parallel.

Forked compilation is generally safer and allows fine grained control of the JVM options of the compilation process as well as decoupling the target JDK from the build JVM.
It will become the default way that Java code is compiled in a future version of Gradle.

### Build multiple variants of a native binary (i)

Gradle is rapidly becoming a capable build system for 'native' code projects.

A key goal of the native binary support in Gradle is to make it easy to create multiple different variants of a native binary
from the same (or similar) set of sources. In Gradle 1.8 is was trivial to generate a shared and static version of the same
library, but in Gradle 1.9 this concept has been taken a lot further. It is now simple to create 'debug' and 'release' variants,
binaries targeting different cpu architectures, or variants built using different tool chains.

Here's an example creating 'debug' and 'release' variants targeting 'i386' and 'x86_64' cpu architectures:

    buildTypes {
        debug {}
        release {}
    }

    targetPlatforms {
        x86 {
            architecture "i386"
        }
        x64 {
            architecture "x86_64"
        }
    }

    binaries.all {
        if (buildType == buildTypes.debug) {
            cppCompiler.args "-g"
        }
    }

Four native binary variants will be produced: 'debugX86', 'releaseX86', 'debugX64' and 'releaseX64'.

As well as `buildTypes` and `targetPlatforms`, it's also possible to define variants based on `toolChains` and custom `flavors`.

Please check out the [user guide section on variants](./userguide/nativeBinaries.html#native_binaries:variants) for complete details
on how to define the set of variants to be produced.

### Improved native binary tool chain support (i)

Gradle 1.9 makes it easier to build native binaries using a variety of tool chains.

New features include:

* A build file can define the set of tool chains used to build
* Visual Studio and Windows SDK installations are automatically discovered and do not need to be in the path
* Use multiple different versions of GCC within the same build invocation
* Build binaries using the [Clang](http://clang.llvm.org) compiler

#### A build file can define the set of tool chains used to build.

    toolChains {
        visualCpp(VisualCpp)
        gcc(Gcc)
    }

#### Visual Studio and Windows SDK installations are automatically discovered and do not need to be in the path

This means you can compile using the Visual C++ tools from the cygwin command prompt. If Visual Studio is installed into
  a non-standard location, you can provide the installation directory directly.

    toolChains {
        visualCpp(VisualCpp) {
            installDir "C:/Apps/MSVS10"
        }
    }

#### Use multiple versions of GCC within the same build invocation

Different `Gcc` tool chain instances can be added with different 'path' values.

    toolChains {
        // Use GCC found on the PATH
        gcc4(Gcc)

        // Use GCC at the specified location
        gcc3(Gcc) {
            path '/opt/gcc/3.4.6/bin'
        }
    }

#### Build binaries using the [Clang](http://clang.llvm.org) compiler

    toolChains {
        clang(Clang)
    }

### Better support for building binaries from C/C++/Assembler (i)

This release improves the support for building binaries from C, C++ and Assembly Language source code. Improvements include:

* Separate plugins for C, C++ and Assembler support
* Separate compiler options for C++ and C sources
* Automatic configuration of source sets for native components

Be sure to check out the [user guide section](./userguide/nativeBinaries.html#native_binaries:languages) for even more details.

#### New 'assembler' and 'c' plugins provide separate language support.

In this release, the 'cpp' plugin now only provides support for compiling and linking from C++ sources. If your project
contains C or Assembly language sources, you will need to apply the 'c' or 'assembler' plugins respectively.

By splitting the support for different languages into separate plugins, this release paves the way for adding
more supported languages in the future.

To complement this change, the `compilerArgs`, `assemblerArgs` and `linkerArgs` on NativeBinary have been
 replaced with language-specific extensions.

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

Note that the language-specific element is only present if the appropriate plugin has been applied. So the 'cCompiler' extension
is only available if the 'c' plugin has been applied.

(Also note that linker arguments are no longer automatically escaped with '-Xlinker' on GCC)

#### Source sets for a native component (executable or library) are automatically configured

In earlier versions, a native component was not automatically associated with any source sets.
To simplify configuration, Gradle now creates and attaches the relevant source sets for every defined `executable` or `library`.

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

This means that given a executable named 'main', a functional source set named 'main' will be created, with a language source
set added for each supported language. So applying the 'assembler' plugin would result in the 'sources.main.asm'
language source set being added, with the conventional source directory `src/main/asm`.

Note that conventional source directories eg: `src/main/cpp` and `src/main/headers` are now only applied if no
source directories are explicitly configured. If you don't define any source directories, the conventions apply.
If you wish to define custom source locations, then _all_ of the source locations must be specified (not just those in
addition to the convention).

### Initializing `Groovy` or a `Scala` project

The `build-init` plugin now ships with two additional templates for initializing a new project:

* `groovy-library` creates a simple Groovy project with Spock as testing framework.
* `scala-library` creates a simple Scala project with scalatest as testing framework.

To initialize a new project just run

<pre>
gradle init --type groovy-library
</pre>

on the commandline.

### HTML dependency report

Thanks to a contribution by [Jean-Baptiste Nizet](https://github.com/jnizet), the `project-report` plugin can now generate an HTML dependency report.

To use the report just apply the plugin:

    apply plugin: "project-report"

And run `gradle htmlDependencyReport` or `gradle projectReport`

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

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Renames in incubating BuildSetup plugin

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
* The `gpp-compiler` plugin was renamed to `gcc`. Class name was changed to `GccCompilerPlugin`.
* Linker arguments are no longer automatically escaped with '-Xlinker' on GCC.
* The conventional source directories eg: `src/main/cpp` and `src/main/headers` are only applied if no source directories are explicitly
  configured. If you wish to define custom source locations, you must define _all_ of the source locations.


## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [John Engelman](https://github.com/johnrengelman)
    - Existence of pom file requires that declared artifacts can be found in the same repository (GRADLE-2034).
    - Fix publishing to Maven Local to follow Maven rules (GRADLE-2762).
* [Jean-Baptiste Nizet](https://github.com/jnizet) - Added an HTML dependency report.
* [Valdis Rigdon](https://github.com/valdisrigdon) - Adds the ability to specify xml:withMessages, text, or emacs for the FindBugs report.
* [Robert Kühne](https://github.com/sponiro)
    - Documentation fixes.
    - Fix a regression when adding Action instances to tasks (GRADLE-2774).
* [Mark Petrovic](https://github.com/ae6rt) - Cucumber test report file formation is failing (GRADLE-2739).
* [Bryan Keller](https://github.com/bryanck) - JAR manifest should be first entry in a archive file (GRADLE-2886).

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
