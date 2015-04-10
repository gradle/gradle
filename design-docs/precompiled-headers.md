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

General idea is to include the PCH as a property on a native source set (C/C++).  The preCompiledHeader value should be a string suitable in a "#include" statement.

    model {
        components {
            mainExe(NativeExecutableSpec) {
                sources {
                    cpp {
                        preCompiledHeader "path/to/my_pch.h"
                        source { }
                        headers { }
                    }
                }
            }
        }
    }

## Implementation
Basically, the approach is to generate a prefix header file containing the specified header to pre-compile.  For VisualCpp, we also need to
generate a source file including the prefix header file.  Then we compile the prefix header and then explicitly include it on the command line
("-include" for gcc/clang, "/Yu" for VisualCpp).

Add a method to DependentSourceSet that allows the user to define a precompiled header file.

    public interface DependentSourceSet extends LanguageSourceSet {
        void preCompiledHeader(String header);
    }

Then, for every source set that defines a precompiled header, we define a prefix header file generation task as well as a header compile task.
In order to do this, we introduce a new PreCompiledHeaderCompile task, PCH compile specs, PCH compilers and LangPCH plugins.  The LangPCH
plugins exist to register that when a pre-compiled header is set on a source set of type X, a compile task of type Y should be used.

For the sourceset, if there is a pre-compiled header set, then we need to check each source file to see if the specified pre-compiled header
is in the list of includes.  We already parse the includes for the incremental compiler, so we can pass that information through as part of
the compile spec.

    public interface NativeCompileSpec extends BinaryToolSpec {
        Map<File, SourceIncludes> getSourceFileIncludes();
        void setSourceFileIncludes(Map<File, SourceIncludes> map);
    }

If a source file does not contain the pre-compiled header as the first included header, then we warn but do not include the PCH args on
the command line.

## Test Cases

- Build fails when developer defines precompiled header, but the precompiled header does not exist.
- Gradle warns when developer defines PCH, but the header is not the first header in the source file.
- When any file included in prefix header file changes, the precompiled header object file and any target binaries are rebuilt.
- Build produces an intermediate file for each variant and language combination.
- Build uses the same compiler settings for PCH compile as variant compile.
- Can set a PCH for a file in headers directory.
- Can set a PCH for a file in an include directory.
- Can set a PCH for a relative file in the source directory.
- For GCC, build of PCH produces <precompiledHeader>.gch
- For MSVC/Clang, build of PCH produces <precompiledHeader>.pch

## Compiler Implementation Details

PCH intermediate files are machine and host specific.  Need to include this into any input snapshotting.

### GCC

Uses .gch extension for precompiled headers (e.g., my_pch.h.gch)

GCC will automatically use a precompiled header if it's found on the include search path first.

GCC will ignore a pre-compiled header if -include is used and the PCH is not in the same directory as the header file or the header is
included in the source file via #include.

The following will work for GCC:

- include option and both header and PCH in the same directory and header is not included via #include
- I option and PCH directory is listed before header directory

[Reference](https://gcc.gnu.org/onlinedocs/gcc/Precompiled-Headers.html)

### Clang

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
- ~~Does this extend well to "lump" or "unity" builds? -- No, not really.~~
- ~~Should we make use of -include or /FI to include precompiled headers in a given source set compilation? (aka prefix headers) -- No~~
- ~~Should probably be easy to turn off PCH for testing/diagnosing a broken build? -- No~~

# Out of Scope

TBD