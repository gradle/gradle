This specification outlines the work that is required to use Gradle to create and use precompiled headers (PCH).

It's a part of the overall effort of improving C/C++/native build support within Gradle.

# Use of Precompiled Headers

Precompiled headers can be used to speed up compilation time if a project uses large header libraries (e.g., Boost), uses header files that include many other header files or uses header files that are included by many source files.

Precompiled headers typically consist of a single header file that includes other header files to be compiled into an intermediate form for the compiler.  System headers and headers from external dependencies are usually good candidates for this technique, since they are unlikely to change.  A change to a precompiled header or any included header requires recompilation of all source units that include the precompiled header.

Precompiled headers must have the same compiler options as the source unit they are used with (or else bad things may happen). 

## Use Cases: Single Precompiled Header

There can only be one precompiled header per source unit compilation.  Some examples of using a precompiled header includes:

- I have a single project with a single native component and want to use a PCH with it.

Using multiple precompiled headers for a single .c or .cpp file is unsupported by MSVC/GCC/Clang; however, a single native component may have multiple sets of sources that each have their own precompiled header. 

## Uses Cases: Not implemented

- I have a single project with multiple native components and want to use the same PCH for multiple components.
- I have multiple projects with native components and want to use a PCH defined in one project across project boundaries.

# DSL (WIP)

General idea is to include the PCH as a property on a native source set (C/C++).  precompiledHeader should be a `Buildable` so we could potentially generate it.

    model {
        components {
            mainExe(NativeExecutableSpec) {
                sources {
                    cpp {
                        precompiledHeader file("path/to/my_pch.h")
                        source { }
                        headers { }
                    }
                }
            }
        }
    }

## Implementation
Basically, the approach is to generate a new source set for any precompiled header that gets defined, then add the compiled header
as a NativeDependencySet of the source set that uses it.

Add a method to DependentSourceSet that allows the user to define a precompiled header file.

    public interface DependentSourceSet extends LanguageSourceSet {
        void preCompiledHeader(Object headerFile);
    }

headerFile will be interpreted as in project.files().

Add a PreCompiledHeaderExportingSourceSetInternal interface to track the relationship between precompiled header source sets and "consuming" source sets.

    public interface PreCompiledHeaderExportingSourceSetInternal extends HeaderExportingSourceSet {
        boolean isPreCompiledHeader();
        void setIsPreCompiledHeader(boolean isPreCompiledHeader);
        DependentSourceSet getConsumingSourceSet();
        void setConsumingSourceSet(DependentSourceSet dependentSourceSet);
    }

Add a method to NativeBinarySpecInternal to track the mapping between "consuming" source sets and precompiled header object outputs.

    public interface NativeBinarySpecInternal extends NativeBinarySpec, BinarySpecInternal {
        Map<DependentSourceSet, FileCollection> getPrecompiledHeaderObjectMappings();
    }

Then, for every source set that defines a precompiled header, we generate a source set and link it to the "consuming" source set.
When we are configuring the compile task for the PCH source set, set the mapping between the consuming source set and the output of the
PCH compile task.

We potentially have to produce different compiler arguments when compiling a precompiled header (MSVC), so we need to specify on the compiletask that
it is compiling a PCH instead of a "normal" source.  So, in NativeCompileSpec:

    public interface NativeCompileSpec extends BinaryToolSpec {
        boolean isPrecompiledHeader();
        void setPrecompiledHeader(boolean true);
    }

Then in NativeCompiler, instead of constructing with a single args transformer, we construct with an args transformer factory.

    public interface ArgsTransformerFactory<T extends BinaryToolSpec> {
        ArgsTransformer<T extends BinaryToolSpec> create(T spec);
    }

Each subclass of NativeCompiler would call the super constructor with a factory that produces the appropriate args transformer for the spec.
For the gcc/clang compilers they probably just produce the same args transformer, but for MSVC, it would probably be different.

For the consuming source set, we also need to know if it should be compiled with a precompiled header.  We add this to AbstractNativeCompileTask:

    public abstract class AbstractNativeCompileTask extends DefaultTask {
        private ConfigurableFileCollection precompiledHeader;
        public void preCompiledHeader(Object header) {
            precompiledHeader.from(header);
        }
    }

Then in NativeCompileSpec, we add the reference to the precompiled header:

    public interface NativeCompileSpec extends BinaryToolSpec {
        File getPrecompiledHeader()
    }

In AbstractNativeCompileTask, we resolve the file collection to a single file (throwing error if >1?) and set it on the spec.
In NativeCompiler, if we find a non-null precompiledHeader, we add the appropriate args to the compile operation.

## Test Cases

- Build fails when developer defines precompiled header, but the precompiled header does not exist.  
- Build fails when developer defines PCH, but the intermediate file cannot be generated.
- Gradle warns when developer defines PCH, but the compiler cannot use it for some reason.
- When any file included in PCH header changes, intermediate file is rebuilt.
- Build produces an intermediate file for each variant and language combination.
- For GCC, build of PCH produces <precompiledHeader>.gch
- For MSVC/Clang, build of PCH produces <precompiledHeader>.pch
- For MSVC, Gradle looks for a <precompiledHeader>.c/cpp.  If it does not exist, we will generate one that #include's <precompiledHeader>.h

Steps for creating PCH for each toolchain are below.

- For all, build of component using PCH inclues PCH-build directory ahead of normal include path.
- For GCC, no other special arguments are needed.
- For Clang, build of component using PCH adds -include-pch for source files that have a dependency on the PCH.
- For MSVC, build of component using PCH adds /Yu<precompiledHeader>.pch for source files that have a dependency on the PCH.

## Compiler Implementation Details

PCH intermediate files are machine and host specific.  Need to include this into any input snapshotting.

### GCC

TODO: Flesh out

Uses .gch extension for precompiled headers (e.g., my_pch.h.gch)

GCC will automatically use a precompiled header if it's found on the include search path first.

GCC will ignore a pre-compiled header if -include is used and the PCH is not in the same directory as the header file or the header is
included in the source file via #include.

The following will work for GCC:

- include option and both header and PCH in the same directory and header is not included via #include
- I option and PCH directory is listed before header directory

[Reference](https://gcc.gnu.org/onlinedocs/gcc/Precompiled-Headers.html)

### Clang

TODO: Flesh out

Similar to GCC, except uses .pch extension.

Clang will not automatically use a precompiled header unless it's included with -include or -include-pch.

Clang will ignore a pre-compiled header if -include is used and the PCH is not in the same directory as the header file.

Clang will also ignore a PCH if the header file is included in the source file (via a "#include" statement).

The following will work for Clang:

- include option and both header and PCH in the same directory and header is not included via #include
- include-pch option and header is not included via #include (PCH and header can be in different directories)

[Reference](http://clang.llvm.org/docs/UsersManual.html#precompiled-headers)

### MSVC

Visual-C++ requires precompiled headers to be created from a source file (.c or .cpp).  Usually, the technique is to create a "boundary" header file (my_pch.h) and a corresponding source file (my_pch.cpp).

my_pch.cpp will consist of:

    #include "some_pch.h"
    #include "my_pch.h"
    #include "not_pch.h"
    // more code could go here

Command-line to create PCH:

    CL /Ycmy_pch.h my_pch.cpp 

The compiler will compile my_pch.cpp up until it reaches the boundary header file (my_pch.h) and produce a file my_pch.pch.  We could control that with /Fp.  Spaces are not allowed after MSVC arguments (e.g., '/Yc filename' is wrong).  This means the PCH source file could also be a "normal" source file and need to be compiled into an object file as well.

For source files that use the precompiled headers, you must compile with /Yumy_pch.h.  The compiler will skip processing anything in the source file before the include for my_pch.h and assume that my_pch.pch contains everything.  This usually means that my_pch.h should be the first file included in a source file.

Like GCC, Visual-C++ has a force include feature (/FI).

MSVC appears to ignore the PCH if /FI is used at all.

The following will work with MSVC:

- /Yu with header and PCH in the same directory as source file
- /Yu and /Fp options with header and PCH in different directories

[Reference](https://msdn.microsoft.com/en-us/library/b4w02hte.aspx)

## IDE Integration

### MSVC

TBD

# Open Questions

- Can we/should we try to generate precompiled header/source files given a list of headers to precompile? 
    - Not sure if we have all the info, but usage statistics on header files would give us some insight so we could provide recommendations for a PCH or automatic generation of a precompiled header.
- At least GCC can tell us if the precompiled header is actually used.  Do the other compilers?
- ~~Does this extend well to "lump" or "unity" builds? -- No, not really.~~
- ~~Should we make use of -include or /FI to include precompiled headers in a given source set compilation? (aka prefix headers) -- No~~
- ~~Should probably be easy to turn off PCH for testing/diagnosing a broken build? -- No~~

# Out of Scope

TBD