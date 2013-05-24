Offer ways to deal with duplicate entries when creating Zip/Tar/Jar/War/Ear archives and when copying files.

# Use cases

Archives can easily end up containing duplicate entries without the user intending this. For example, when the output directories
for classes and resources are reconfigured to point to the same directory (which can, for example, be necessary for tests involving JPA entities),
the resulting Jar archive will contain each file twice. This is not only undesirable, but can easily go unnoticed because no warning is issued.

Users should have an easy way to prevent duplicate file (path)s from being added to an archive or copied to a directory. Nevertheless, the
cases where duplicates are desired should still be supported. One rationale for supporting duplicates is that a Jar archive should be able to represent any class path,
which may of course contain duplicates. For certain resource files (e.g. service descriptors) it is even essential that the class path can contain
duplicates. A concrete example where duplicate (resource) paths may be desirable is a fat Jar.

# Implementation plan

## Story: User declares strategy for dealing with duplicates in archives and copy output

This story introduces the concept of a strategy for duplicate files.

1. Add a `DuplicatesStrategy` enum with values `include` and `exclude`.
2. Add a `duplicatesStrategy` property to `FileCopyDetails` with default value `include`.
3. Add a `CopySpecVisitor` implementation that honours the `duplicatesStrategy` specified for each file.
4. For the `Copy` type, this visitor (or perhaps some subtype) should log a deprecation warning for a
   duplicate file with duplicate strategy of `include`. This will become an error in Gradle 2.0.

### User visible changes

A user can specify the duplicates strategy for any file:

    jar {
        eachFile { details -> details.duplicatesStrategy = 'exclude' }
    }

### Test coverage

- Duplicate files are included in a ZIP and TAR by default.
- Duplicate `MANIFEST.MF` files are included in a JAR by default.
- When duplicate strategy is `exclude`, the only the first file with a given path is added to the archive.
- The `Copy` task warns when a duplicate file is copied by default.
- Can assemble a JAR where duplicate service files are added but other duplicate files are excluded.
- The `Copy` task does not warn when a duplicate file is excluded.
- A file that is renamed to have the same path as some other file is treated as a duplicate file.

## Story: User declares duplicates strategy for all files processed by a copy spec

This story adds a convenience to specify the duplicates strategy for all files in a copy operation:

1. Add a `duplicatesStrategy` property to `CopySpec`. This defaults to `include` for a root copy spec and is inherited
   by child copy specs.

### User visible changes

A user can specify the duplicates strategy for a group of files:

    zip {
        duplicatesStrategy = 'exclude'
        into('META-INF/services') {
            from 'some-dir'
            duplicatesStrategy = 'include'
        }
    }

### Test coverage

- Can assemble a JAR where duplicate service files are added but other duplicate files are excluded.
- When duplicate strategy is `exclude`:
    - The manifest defined by the `Jar.manifest` property has priority over files defined using `Jar.metaInf { ... }`.
    - The files defined using `Jar.metaInf { ... }` have priority over files added using other copy specs.
    - The descriptor defined by the `Ear.deploymentDescriptor` property has priority over files defined
      using `Ear.metaInf { ... }`.
    - A file defined by `Ear.lib { ... }` has priority over files added using other copy specs.
    - The descriptor defined by the `War.webXml` property has priority over files defined using `War.webInf { ... }`.
    - The files defined by the `War.classpath` property have priority over files defined using `War.webInf { ... }`.
    - The files defined by `War.webInf { ... }` have priority over files added using copy specs.

## Story: Simpler fine-grained control over copying of certain files

This story adds a convenience to specify fine-grained control over all files in a copy operation:

1. Add a `matching(String, Closure)` method to `CopySpec`. This is equivelent to using `eachFile()` for all files whose path match
   the specified Ant-style pattern.
2. Add a `notMatching(String, Closure)` method to `CopySpec`. This is equivelent to using `eachFile()` for all files whose path
   does not match the specified Ant-style pattern.

### User visible changes

    jar {
        matching('/META-INF/services/**') { duplicatesStrategy = 'include' }
        matching('**/*.template') {
            expand(someProp: 'some-value')
            rename('(.*)\\.template', '\\$1')
        }
    }

### Test coverage

- Can filter some files in the JAR file and leave some others unmodified.
- Can chain the rules together, where the first rule renames a file that matches the second rule.
- Rules are inherited by a child copy spec.

## Story: User specifies that copy operation should fail when duplicate files are present

Add `fail` and `warn` values to `DuplicatesStrategy` enum and the corresponding implementation

### Test coverage

- The copy operation fails when duplicate files are present.
- The copy operation warns when duplicate files are present and includes the duplicates in the result.

## Story: User specifies input and output encoding for text files

Currently, the JVM's default character encoding is used when filtering files. This story allows both the input and output
encoding to be specified separately.

## Story: Simple token expansion

This story adds a simpler alternative to the `expand()` filter.

## Story: Filter operation to convert input file to ASCII with unicode escapes

This story adds a filter that can take an input file and transform it to an output file encoded in ASCII, with non-ASCII
characters replaced with Java Unicode escapes. Using this filter implicitly sets the output encoding to 'US-ASCII'.

## Story: Filter operation that transforms the entire text of an input file

This story adds a convenience to transform the entire contents of a text file as a single string.

## Story: Convenience to rename the file and directory paths

Currently, it is possible to change the name of a file. This story adds equivalent convenience methods to rename the whole
path of a file or directory.

## Story: Copy empty directories when no input files

## Story: Allow empty directories to be defined in output

## Story: Allow user fine-grained control over all files in an archive

## Story: Fix file renaming so that file is not copied twice

## Story: Fix file renaming so that original directory is not added

## Story: Use UTF-8 as default path encoding in archives

# Open issues

Since duplicates are in most cases undesirable, but can nevertheless occur without the user doing something obviously wrong,
the default strategy should be either "ignore duplicates" or "warn on duplicates". Defaulting to "ignore duplicates" is a breaking change;
defaulting to "warn on duplicates" is probably an acceptable change even for 1.x.

The cases "same archive path, same file content" and "same archive path, different file content" could be treated differently (e.g. ignore vs. warn).

For Jar and War archives, class files and resource files could be treated differently.

Combining the previous two ideas, the following might be a useful strategy for Jar archives:

* Class file duplicate with same content - ignore
* Class file duplicate with different content - fail
* Resource file duplicate with same content - ignore
* Resource file duplicate with different content - add

Different archive types could have different default strategies. For example, for Zips and Tars it probably make even less sense
to add duplicates than for Jars and Wars.