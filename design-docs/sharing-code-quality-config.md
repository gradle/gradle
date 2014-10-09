Gradle's CheckStyle, PMD, and FindBugs plugins allow the respective tools to be configured via (their own) configuration files. In an organization, it can be desirable to share the same configuration files between many builds. One potential approach for sharing configuration files is to package them as Jars, which are then shared via a binary repository. Another approach is to share configuration files as HTTP resources. This spec is about the binary repository approach, which benefits from all the advantages of Gradle's dependency resolution and caching features.

For PMD, sharing configuration files via a binary repository is already possible. For example:

    configurations {
      pmdRules
    }
    dependencies {
      pmdRules "my.org:my-pmd-rules:1.0"
    }
    tasks.withType(Pmd) {
      pmdClasspath += configurations.pmdRules
      ruleSets = ["my_pmd_ruleset.xml"]
    }

(Note: if `pmdClassPath` could be configured via `pmd { ... }`, the syntax would become a bit nicer.)

This leaves Checkstyle and FindBugs.

# Use cases

Enforce organization code standards by sharing the same Checkstyle, PMD, and FindBugs configuration files between many Gradle builds. 

# Implementation plan

## Allow Checkstyle configuration files to be loaded from (Checkstyle) class path

### User visible changes

Introduce a `String configResource` property on the `Checkstyle` task. Only one of `configResource` and `configFile` can be set.

### Implementation

Pass on the value of the `configResource` property to the `config` property of the Ant task.

### Test coverage

* Package a ruleset file as a Jar
* Add the Jar to `Checkstyle#checkstyleClasspath`
* Set `Checkstyle#configResourceName` appropriately
* Execute the Checkstyle task and verify that the ruleset takes effect

## Allow FindBugs bug filter include/exclude files to be loaded from (Findbugs) class path

### User visible changes

Introduce `String includeFilterResource` and `excludeFilterResource` properties on the `FindBugs` tasks. Only one of `includeFilter` and `includeFilterResource` can be set. Only one of `excludeFilter` and `excludeFilterResource` can be set.

### Implementation

If configured, extract include/exclude filter resources from `pluginClasspath` (or `findbugsClasspath`?), write them to a file, and pass the files to FindBugs.

### Test coverage

* Package bug filter include/exclude files as a Jar
* Add the Jar to the FindBugs (plugin) class path
* Set `FindBugs#includeFilterResource` and `FindBugs#excludeFilterResource` appropriately
* Execute the FindBugs task and verify that the bug filters takes effect

# Open issues

* Instead of introducing new properties, the existing properties could be generalized to accept either a class path resource name or a file location. However, this would constitute a breaking change, as the properties' type would have to change from `File` to `Object`. Another option would be to introduce new generalized properties while deprecating the existing ones.
* FindBugs: Resource files could be extracted using `project.copy`/`zipTree`, or accessed using a class loader. Extraction could happen in the Gradle process or the FindBugs worker process.
* Do we want to allow all of this to be configured via the `checkstyle`, `pmd`, and `findbugs` extensions? This would require to also add the respective `classpath` properties to the extensions.

# Alternative: Generic solution

This proposal is in some ways more ambitious, as it is a more general solution.

## New type: TextResource

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













