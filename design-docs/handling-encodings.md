# Encoding

Updated: __2016/03/19__
Status: __Under Review__

This document describes the rules and conventions for handling the character encoding of file contents in general, and file names 
and comments in archives. It applies to tasks which need to read, write and transform text files, as well as tasks which create
archives (zip files, tar files, etc.)

## Use Cases

### File Content Encoding

Any time we need to generate a text file, or to read, transform and write the contents of a text file, we need to know what 
character encoding(s) to use.

Common situations include:

* Copying and filtering a file from one location to another.
* Creating an archive (zip, tar, etc.).
* Transforming text in a file.

[GRADLE-1267](https://issues.gradle.org/browse/GRADLE-1267) describes just one instance of where this functionality is needed.

### File Name Encoding

Each file system has an idea of what character sets are used to represent file names in the filesystem, and it's important to be 
able to use filenames in archives that are encoded using the encoding of the target file system, which is not necessarily the same
as the file system where the archive is created.

This use case is why the Zip task and the War task have an `encoding` option added in 
[Pull Request 499](https://github.com/gradle/gradle/pull/499) which allows creating a zip archive which contains files whose names 
are encoded with a character encoding different from the platform default encoding.

## Solution

This design generally proposes that any task which needs to read and write text or encode file names in an archive, should support two configurable settings:

1. `contentEncoding` - The encoding to be used to convert bytes to characters (when reading), and characters to bytes (when writing).
2. `fileNameEncoding` - The encoding to be used for names of files in an archive.

Both properties default to platform default encoding if they are not specified in the task's configuration block.

Notes:

 - reading and writing characters isn't limited to files. It also applies to archive entries, network communication, etc.
 - some tasks might need to use an encoding to write that is different from the encoding used to read. For the most frequent case,
   i.e. copying and transforming files, it's a reasonable assumption to expect the developer to choose the target encoding for the
   source files, and thus to use a single `contentEncoding` both for reading and writing. By the way, when not filtering the 
   files, the Copy task treats them as binary files, and thus uses the same encoding for the source and target files.

## Implementation Plan

The CopySpec interface and all its implementations should support a `contentEncoding` option, used only when the spec filters 
the content (otherwise, the bytes of the source files are copied as is to the destination). This allows filtering the content 
correctly from and to the file system, as well as from and to archives. 

For the Zip, War, Ear and Tar tasks, we should add support a `fileNameEncoding` encoding option, that should thus be set on their
parent AbstractArchiveTask.

To be easier to specify in the build file, these properties should be of type `String`, and not of type `Charset`.

## Testing Plan

We need to add tests which cover each of these scenarios:

- The default platform encoding is honored when either of these properties is not set.
- When `contentEncoding` is set to something other than the default platform encoding, it is honored.
- When `fileNameEncoding` is set to something other than the default platform encoding, it is honored.
- When a character cannot be properly converted into the specified encoding, a clear error message is displayed to the user.

## Documentation Plan

- The CopySpec interface documentation should document the `contentEncoding` property and specify the default value if the property is not set.
  Since the Copy task inherits from CopySpec, that should document the task automatically.
- The AbstractArchiveTask should document the `fileNameEncoding` property and specify the default value if the property is not set.

## Open Questions

- The Zip, War and Ear tasks are already using `encoding` as the name of its setting which really deals with `fileNameEncoding`. The best option
  should be to deprecate this property, make the setter set the new `fileNameEncoding` property, and the getter return the `fileNameEncoding` 
  property.
- The name `encoding` is much more common practice in things like the xml standards, ant script, editor configurations, etc. 
  but it's less clear than `contentEncoding`, and is in conflict with the existing `encoding` property of the Zip, War and Ear tasks. Using
  it would make it impossible to stay backward-compatible.
- Should the property accept a null value (as it does in the Zip task) and be null by default, and/or should it be set to the default charset?
- Are there other existing tasks which should deal with character encoding in a better way?
- Is it a wise choice to use the platform encoding as the default file name encoding? 
    - The JDK ZipOutputStream defaults to UTF8
    - Apache comons ZipArchiveOutputStream defaults to UTF8
    - Apache Ant's ZipOutputStream, which is used by Gradle now, defaults to the platform default encoding, and changing the default would be a  
      backward incompatibility.
- Should the Tar task really allow specifying an encoding, and should it default to the platform encoding? It defaults to ASCII, and [the standard](http://www.gnu.org/software/tar/manual/html_node/Standard.html) doesn't seem to allow anything else. 