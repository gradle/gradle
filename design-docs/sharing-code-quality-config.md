
# Stories

## ~~Introduce TextResource~~

A new interface will be added:

    package org.gradle.api.resources
    
    interface TextResource extends Buildable {
      String asString() // returns the text
      
      File asFile() // returns an “anonymous” file containing the text
      
      String getDescription() // some opaque description of what the text resource is (i.e. its origin, not its content)
    }
    
The contract of the `asFile()` method is that there are only three guarantees:

1. The file is readable by the current process
2. The file contains the text represented by this resource
3. During a build, calling `asFile()` on the same resource will always return the same file on the filesystem

Clients of this interface should make no further assumptions about the location, permission, timestamp etc. of the file.

All methods are idempotent.

Implementations may load lazily, but should perform any caching necessary in order to support `as*()` methods being called multiple times.

Implementations should meaningfully implement `equals()` where possible, but not strictly based on actual text content.
For example, an implementation backed by a `File` and another backed by `FileCollection` may not be comparable even though the FileCollection contains only the exact file of the `File` based impl being compared to.

## Creating a `TextResource`

New methods will be added to `ResourceHandler` (which is already available via `project.getResources()`) that adapt objects to the `TextResource` interface.

There will be initially be the following methods:

    // Simple file wrapper
    TextResource text(File file)
    TextResource text(File file, String charset)
    
    // Lazily calls .singleFile, transfers buildable information
    TextResource text(FileCollection fileCollection)
    TextResource text(FileCollection fileCollection, String charset)
    
    // Lazily calls .singleFile, expecting file to be an archive that can be implicitly decompressed/unpacked based on file extension
    // path argument refers to entry within archive, transfers buildable information
    // asFile() of return value generates tmp file
    TextResource archiveText(FileCollection fileCollection, String path)
    TextResource archiveText(FileCollection fileCollection, String path, String charset) 

(methods not accepting a charset param assume JVM's platform encoding).

`asFile()` method of `text()` methods return the actual file.

`asFile()` method of `archiveText()` method returns a temporary file located in the project's tmp dir.
The name of the tmp file will be based on hashing the absolute path of the archive, and a hash of the path of the entry to extract.

    build/tmp/resources/archives/«absolute archive path hash»/«path hash»/«file name».«ext»

That is, the location of the tmp file is predictable and consistent.

## Retrofitting  `TextResource` to existing tasks

    class Checkstyle extends DefaultTask {
        
        @Input
        TextResource config
        
    }
    
The mechanism for `@Input` will require changes in order to handle `TextResource` objects:

1. `TextResource` objects are not serialized and compared with equals() like other properties
2. A dependency is added on the resource's `getTaskDependency()`
3. A file input is implicitly added for the resource's `asFile()` return

Treating the input implicitly as a file input allows reuse of the up-to-date checking mechanism for files, which is preferable (at least initially) to comparing the content more directly.
This requires that for a given resource, `asFile()` returns a deterministic file.
If this is not the case, the task is always going to out of date as a moved/renamed file is considered changed.

This approach will not produce entirely accurate results in that it will produce false positives for changes.
That is, if the property changes to a different representation but with exactly the same content, we will consider it changed.
This can be improved later.

### Open Questions

- Should `TextResource` methods throw `IOException` - (I don't think there's a point - LD) Currently throw UncheckedIOException
- Should `TextResource.as*()` throw if called when the `getTaskDependencies()` are unsatisfied (e.g. can't call this at config time)
- For the to-be deprecated `File` properties (e.g. `Checkstyle.configFile`), do we support calling the getter if the `TextResource` property was set? Yes
- It might be better to have separate methods rather than overloads (e.g. `resources.text()`, `resources.fileText()`, `resources.archiveEntryText()`, or 
  `resources.text.fromString()`, `resources.text.fromFile()`, `resources.text.fromArchiveEntry()`). This way we could support the usual coercions (e.g. `String`->`File`), 
  although we'd still have to deal with the fact that the same method needs to accept both files and single-element file collections.  
- It feels that the `resources.text()`/`resources.archiveText()` methods compensate for limitations in Gradle's file APIs. For example, 
  it would be more natural to select an archive entry using a file (tree) API and pass the result to `resources.text()`, rather than 
  using `resources.archiveText()`.
- Should there be a way to query the character encoding of the file returned by `TextResource#asFile`? (As long as we return the 
  original file that the resource was created from (if any), we can't standardize on one particular encoding.)
- Should we leverage the existing ReadableResource/ResourceException/MissingResourceException types?













