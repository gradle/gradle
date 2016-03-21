# Encoding

Updated: __2016/03/18__
Status: __Under Review__

This document describes a solution for handling file-system and content encoding for tasks which need to convert between bytes and character sets.

## Use Cases

### File Content Encoding

Any time we need to convert the contents of a file represented as an array of bytes into a set of characters, we need to know what encoding to use for the conversion, or we cannot expect to be able to manipulate those characters correctly.

Common situations include:

* Copying a file from one location to another.
* Expanding an archive (zip, tar, etc.) into some directory.
* Transforming text in a file.

[GRADLE-1267](https://issues.gradle.org/browse/GRADLE-1267) describes just one instance of where this functionality is needed.

### File Name Encoding

Each filesystem has an idea of what character sets are used to represent files in the filesystem, and it's important that we be able to be able to read and write filenames to the filesystem in a way that won't cause errors.

This use case is why the Zip task had an `encoding` option added in [Pull Request 499](https://github.com/gradle/gradle/pull/499) which allows expanding a zip archive which contains files whose names are encoded with a differnt character set than the default JVM `file.encoding` correctly.

## Solution

This design generally proposes that any task which needs to support multiple encodings for file contents or multiple encodings for file names, should support two configurable settings:

1. `encoding` - The encoding to be used for converting from the bytes which make up the contents of a file to a character set.
1. `fileNameEncoding` - The encoding to be used for names of files in the filesystem.

Both properties default to the value of the JVM System Property `file.encoding` if they are not overridden in the task's configuration block.

## Implementation Plan

For each of the Copy Zip and Tar tasks, we should add support for both an `encoding` and `fileName` encoding option on the associated `*Spec` class.

Every place in the code which supports these tasks that needs to be updated to handle the new settings.

## Testing Plan

For each of the Copy Zip and Tar tasks, we need to add tests which
cover each of these scenarios:

- The JVM system property `file.encoding` is honored when neither
field is set.
- When `encoding` is set to something other than the `file.encoding`
system property's value, it is honored.
- When `fileNameEncoding` is set to something other than the
`file.encoding` system property's value, it is honored.
- When a character cannot be properly converted into the specified
encoding, a clear error message is displayed to the user.

## Documentation Plan

- We add a page to the user-guide which generically talks about how to configure the content encoding and file name encoding for tasks describing these two settings.

For each task which is enhanced in this way, we update:

- The page in the userguide for that task to link to the generic description of the encoding settings.
- The page in the DSL reference to include a description of how to use the new encoding-related settings.
- Add a sample to our samples showing how to use the settings.

## Open Questions

- The Zip Task is already using `encoding` as the name of it's setting which really deals with `fileEncoding` Not sure how to deal with changing the meaning of this setting in a backwards-compatible way.
- The name `contentEncoding` has also been proposed to make it clear taht we are talking about the encoding used for converting the contents of a file. It is appealing, but simply `encoding` is much more common practice in things like the xml standarads, ant script, editor configurations, etc.
