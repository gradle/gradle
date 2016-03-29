# Encoding

Updated: __2016/03/24__
Status: __Under Review__

This document describes the rules and conventions for handling the character encoding of file contents in general, and metadata
(file names and comments) in archives. It applies to tasks which need to read, write and transform text files, as well as tasks which create
archives (zip files, tar files, etc.)

## Use Cases

### Content Encoding

Any time we need to generate a text file, or to read, transform and write the contents of a text file, we need to know what 
charset(s) to use.

Common situations include:

* Copying and filtering a file from one location to another.
* Creating and reading an archive (zip, tar, etc.).

[GRADLE-1267](https://issues.gradle.org/browse/GRADLE-1267) describes just one instance of where this functionality is needed.

### Metadata Encoding

Metadata in zip files can be encoded with a variety of charsets. 

POSIX-compliant tar files, on the other hand, only use UTF8 for metadata. But non-compliant implementations can use other
charsets and Gradle should be able to read them. 

Jar, war and ear files only use UTF8 for metadata. 

In Gradle however, up to version 2.12 at least, the Zip, Jar, War and Ear tasks all have an `encoding` option added in 
[Pull Request 499](https://github.com/gradle/gradle/pull/499) which allows specifying a metadata charset, which defaults to
the platform default encoding. The name of this option is confusing, and the default value is not correct. Moreover, jar, war and ear
metadata should always be encoded in UTF8 instead of the platform default encoding. The Tar task should use UTF8, but it
uses the default platform encoding.

## Solution

### Content encoding

This design generally proposes that any task which needs to read and write text should specify a charset used to encode/decode 
characters into bytes.

Notes:

 - reading and writing characters isn't limited to files. It also applies to archive entries, network communication, etc.
 - some tasks might need to use a charset to write that is different from the charset used to read. For the most frequent case,
   i.e. copying and filtering files, it's a reasonable assumption to expect the developer to choose the target encoding for the
   source files, and thus to use a single filtering charset both for reading and writing. By the way, when not filtering the 
   files, the Copy task treats them as binary files, and thus uses the same encoding for the source and target files.

### Metadata encoding

The Zip task, which produces zip files, should allow specifying a metadata charset used to encode/decode the metadata
of the archive. This should default to UTF8. 

Jar, War, Ear and Tar tasks should not allow specifying a metadata charset, or at the very least should reject any metadata 
charset other than UTF8.

When reading from zip files using `ZipFileTree`, we should support a metadata charset option as well.

When reading from tar files using `TarFileTree`, we should support a metadata charset option so that we can handle reading 
non-POSIX tar files, like the tar files created with current and older versions of Gradle, or with other tools.

## Implementation Plan

### Content encoding

`CopySpec` should get a new `filteringCharset` property of type `String` which is the name of the `Charset` which will be used for 
encoding and decoding the contents of a file on either side of passing the contents to the closure(s) specified by the various 
filter methods of CopySpec. 

The default value of `filteringCharset` should be the name of the platform default charset. That will maintain backward 
compatibility, since up to Gradle 1.12 at least, the platform default charset is always used.

### Metadata encoding

The Zip task should support a `metadataCharset` option, or type String, defaulting to UTF8. Unfortunately, the Jar task 
inherits from Zip, and should not use any `metadataCharset` other than UTF8. The setter of this property should thus be 
overridden to throw an `UnsupportedOperationException`. War and Ear inherit from Jar, and will thus automatically benefit
from this behavior.

The Tar task should use UTF8 rather than the default platform charset to encode its metadata, and the POSIX/PAX mode when creating entries.

ZipFileTree should use an additional `metadataCharset` option, defaulting to UTF8.

TarFileTree should use an additional `metadataCharset` option, defaulting to UTF8.

The `encoding` property of the Zip task (inherited by Jar, War and Ear) should be deprecated. Its getter and setter shoud delegate 
to the new `metadataCharset` getter and setter.

The setters for these two charset properties should not accept null, and should validate that the given String is a valid `Charset` name,
and produce a clear error message if it isn't.

## Testing Plan

We need to add tests which cover each of these scenarios:

- The default encoding is honored when `filteringCharset` or `metadataCharset` is not set.
- When `filteringCharset` is set to something other than the default platform encoding, it is honored.
- When `metadataCharset` is set to something other than the default UTF8 value in Zip, ZipFileTree and TarFileTree, it is honored.
- When `metadataCharset` is set in Jar, an `UnsupportedOperationException` is thrown.
- When a specified charset is not a valid charset name, an exception is thrown immediately, with a clear error message.

## Documentation Plan

- The CopySpec interface documentation should document the `filteringCharset` property and specify the default value if the property is not set.
  Since the Copy task inherits from CopySpec, that should document the task automatically.
- The Zip task should document the `metadataCharset` property and specify the default value if the property is not set.
- The Jar, War and Ear tasks should document the `metadataCharset` property and specify that it should not be set and will always use the 
  default UTF8 value.

## Note

The code base should be investigated in order to find properties and method arguments with names like `encoding`, `charset`, etc., and replace them with either `metadataCharset` or `filteringCharset` if those names are more accurate.

## Open Questions

