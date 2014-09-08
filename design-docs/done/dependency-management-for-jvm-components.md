### Story: Build author defines JVM library (DONE)

#### DSL

Project defining single jvm libraries

    apply plugin: 'jvm-component'

    jvm {
        libraries {
            main
        }
    }

Project defining multiple jvm libraries

    apply plugin: 'jvm-component'

    jvm {
        libraries {
            main
            extra
        }
    }

Combining native and jvm libraries in single project

    apply plugin: 'jvm-component'
    apply plugin: 'native-component'

    jvm {
        libraries {
            myJvmLib
        }
    }
    nativeRuntime {
        libraries {
            myNativeLib
        }
    }

#### Implementation plan

- Introduce `org.gradle.jvm.JvmLibrary`
- Rename `org.gradle.nativebinaries.Library` to `org.gradle.nativebinaries.NativeLibrary`
    - Similar renames for `Executable`, `TestSuite` and all related classes
- Introduce a common superclass for `Library`.
- Extract `org.gradle.nativebinaries.LibraryContainer` out of `nativebinaries` project into `language-base` project,
  and make it an extensible polymorphic container.
    - Different 'library' plugins will register a factory for library types.
- Add a `jvm-component` plugin, that:
    - Registers support for `JvmLibrary`.
    - Adds a single `JvmLibraryBinary` instance to the `binaries` container for every `JvmLibrary`
    - Creates a binary lifecycle task for generating this `JvmLibraryBinary`
    - Wires the binary lifecycle task into the main `assemble` task.
- Rename `NativeBinariesPlugin` to `NativeComponentPlugin` with id `native-component`.
- Move `Binary` and `ClassDirectoryBinary` to live with the runtime support classes (and not the language support classes)
- Extract a common supertype `Application` for `NativeExecutable`, and a common supertype `Component` for `Library` and `Application`
- Introduce a 'filtered' view of the ExtensiblePolymorphicDomainObjectContainer, such that only elements of a particular type are returned
  and any element created is given that type.
    - Add a backing `projectComponents` container extension that contains all Library and Application elements
        - Will later be merged with `project.components`.
    - Add 'jvm' and 'nativeRuntime' extensions for namespacing different library containers
    - Add 'nativeRuntime.libraries' and 'jvm.libraries' as filtered containers on 'components', with appropriate library type
    - Add 'nativeRuntime.executables' as filtered view on 'components
    - Use the 'projectComponents' container in native code where currently must iterate separately over 'libraries' and 'executables'

#### Test cases

- Can apply `jvm-component` plugin without defining library
    - No binaries defined
    - No lifecycle task added
- Define a jvm library component
    - `JvmLibraryBinary` added to binaries container
    - Lifecycle task available to build binary: skipped when no sources for binary
    - Binary is buildable: can add dependent tasks which are executed when building binary
- Define and build multiple java libraries in the same project
  - Build library jars individually using binary lifecycle task
  - `gradle assemble` builds single jar for each library
- Can combine native and JVM libraries in the same project
  - `gradle assemble` executes lifecycle tasks for each native library and each jvm library

### Story: Only attach source sets of relevant languages to component (DONE)

- Don't attach Java source sets to native components.
- Don't attach native language source sets to jvm components.

This story will involve defining 'input-type' for each component type: e.g. JvmByteCode for a JvmLibraryBinary and ObjectFile for NativeBinary.
A language plugin will need to register the compiled output type for each source set. Then it will be possible for a component to only
attach to those language source sets that have an appropriate output type.

### Story: Reorganise 'cpp' project to more consistent with 'language-jvm' project (DONE)

- ~~Move tasks/plugins/etc that are used to compile native languages for the native runtime into `org.gradle.language.*`~~
- ~~Move Visual Studio and CDE related classes into new subproject `ide-native`~~
    - ~~Move ide-specific integration tests as well~~
- ~~Move language-specific classes (`org.gradle.language.*`) out of `cpp` into a new subproject `language-native`~~
    - ~~Move language-related integration tests as well, breaking into a better package structure~~
- ~~Rename the remaining `cpp` subproject to `platform-native`~~
    - ~~Rename packages `org.gradle.nativebinaries.*` to `org.gradle.nativeplatform.*`~~
    - ~~Move integration tests into `platform-native`, breaking into a better package structure~~
- ~~Move runtime-specific classes (`org.gradle.runtime.*`) out of `language-jvm` into new subproject `platform-jvm`~~
- ~~Add new `language-java` subproject and `language-groovy` subprojects: and move in any java/groovy-specific classes~~
    - ~~`language-jvm` should be for common base infrastructure~~
- ~~Miscellaneous~~
    - ~~Enable classycle for all projects~~
    - ~~Split NativeSamplesIntegrationTest for subprojects~~

