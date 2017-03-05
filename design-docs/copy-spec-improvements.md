This spec describes improvements to the copy spec and copy tasks.

# Use cases

## Handle duplicate entries

Archives can easily end up containing duplicate entries without the user intending this. For example, when the output directories
for classes and resources are reconfigured to point to the same directory (which can, for example, be necessary for tests involving JPA entities),
the resulting Jar archive will contain each file twice. This is not only undesirable, but can easily go unnoticed because no warning is issued.

Users should have an easy way to prevent duplicate file (path)s from being added to an archive or copied to a directory. Nevertheless, the
cases where duplicates are desired should still be supported. One rationale for supporting duplicates is that a Jar archive should be able to represent any class path,
which may of course contain duplicates. For certain resource files (e.g. service descriptors) it is even essential that the class path can contain
duplicates. A concrete example where duplicate (resource) paths may be desirable is a fat Jar.

## Handle symbolic links

# Implementation plan

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

## Story: Allow symbolic link traversal strategy to be declared for a file tree

A file tree is a hierarchy of file tree elements. Each element can be one of the following types:

1. A regular file. Has binary content that can be read. Does not have any children.
2. A directory. Is itself a file tree, so has child elements. Does not have any binary content.
3. A symbolic link that refers to some target. The target may reference some other element in the file tree, or
   some file outside the file tree, or a missing file.

Currently, when visiting the elements of a file tree, symbolic link elements are substituted with the target
of the link. For tar file trees, symbolic links are substituted with a zero-sized regular file, but this can be
considered a bug.

This story provides a means for the visitor of a file tree to declare how to traverse links:

1. Introduce `FileTreeElement.Type` enum type with values `REGULAR_FILE`, `DIRECTORY`, `SYMBOLIC_LINK`.
2. Add `FileTreeElement.getType()` method. All implementations return either `REGULAR_FILE` or `DIRECTORY` (for now).
3. Introduce a `SymlinkStrategy` enum type. This strategy determines how a symbolic link is traversed:
    - `FOLLOW` traverses the file/directory referenced by the symbolic link.
    - `PRESERVE` traverses the symbolic link itself.
4. Add `FileTree.visit(Action<? super FileVisitDetails> action)`
5. Add `FileTree.visit(Action<? super FileVisitDetails> action, SymlinkStrategy strategy)`
    - Default implementation delegates to `visit(action)` (ie the tree does not support symbolic links).
6. Change the implementation of the file system file tree to honour the symlink strategy.
    - When the strategy is `PRESERVE` visit the symbolic link but do not visit whatever it references.
      `FileElementTree.getType()` should return `SYMBOLIC_LINK`.
    - When the strategy is `FOLLOW` visit the target of the symbolic link. `FileElementType.getType()` should not
      return `SYMBOLIC_LINK`, but the type of the target file.
6. Add the appropriate methods to `FileSystem` to allow reading of symbolic links, and an initial implementation that
   only works on Java 7.

### Test cases

- Visit a file system tree that contains a symbolic link to a file and a symbolic link to a directory.
    - Verify that symbolic links are followed when strategy is `FOLLOW`.
    - Verify that symbolic links is visited but not followed when strategy is `PRESERVE`.
- Visit a file system tree that contains a symbolic link whose target does not exist:
    - Verify that symbolic links is visited but not followed when strategy is `PRESERVE`.
    - Verify that traversal fails when strategy is `FOLLOW`.
- For the above cases, verify that `getType()` returns the correct value.

## Story: Allow symbolic links in file trees to be copied

This story adds allows a symbolic link to be copied when visiting the elements of a file tree:

1. Change the contract of `FileTreeElement.copyTo(File file)` so that:
   - When type == `REGULAR_FILE`, copies the file content to the given target file.
   - When type == `DIRECTORY`, creates a directory at the given location.
   - When type == `SYMBOLIC_LINK`, creates a symbolic link at the given location using `FileSystem`. Note that
     type == `SYMBOLIC_LINK` when strategy == `PRESERVE`.
2. Change the contract of `FileTreeElement.copyTo(OutputStream stream)` so that:
    - When type == `REGULAR_FILE`, copies the file content to the given stream.
    - When type != `REGULAR_FILE` throw an exception.

Note: there's a minor breaking change here, as it is now an error to call copyTo(OutputStream) on a directory element.

### Test cases

- Visit a file system tree that contains a symbolic link to a file and a symbolic link to a directory:
    - When strategy is `FOLLOW`, use `copyTo()` to create a copy of the tree. Verify that that symbolic links are followed.
    - When strategy is `PRESERVE`, use `copyTo()` to create a copy of the tree. Verify that that symbolic link is copied.
- Visit a file system tree that contains a symbolic link whose target does not exist:
    - When strategy is `FOLLOW`, use `copyTo()` to create a copy of the tree. Verify traversal fails.
    - When strategy is `PRESERVE`, use `copyTo()` to create a copy of the tree. Verify that that symbolic links is copied.

Can extend the tests from the previous story.

## Story: Expose symbolic links in tar file trees

1. Implement support for reading symbolic link elements in tar files. This means that when strategy is `PRESERVE`, then
   `FileTreeElement.getType()` and `copyTo()` have the correct behaviour.

### Test cases

- Visit a tar file tree that contains symbolic links with strategy `PRESERVE`:
     - Use `copyTo()` to create a copy of the tree. Verify that symbolic links are preserved.
     - Verify that `getType()` returns the correct value for symbolic links.

## Story: User declares that symbolic links should be preserved when copying a file tree to the local file system

This story adds support for preserving symbolic links when copying from the file system.

1. Add `symlinkStrategy` property to `CopySpec` of type `SymlinkStrategy`. Should default to `FOLLOW`.
2. By default, fail when strategy is `PRESERVE`.
3. When copying to the file system, preserve symbolic links when strategy is `PRESERVE`.
4. Invoke `eachFile()`, `filesMatching()` and `filesNotMatching()` only on elements whose type is `REGULAR_FILE`.

### Test cases

- Copy a file tree that contains symbolic links to a file system directory:
     - Use strategy `PRESERVE` and verify that the symbolic links are preserved. Verify that `eachFile()` is called
       only for files, and not for any symbolic links (including those that refer to a file) or directories.
     - Use strategy `FOLLOW` and verify that the symbolic links are followed. Verify that `eachFile()` is called
       for files and symbolic links that refer to a file, but not for directories or symbolic links that refer to a directory.
- Attempt to create a zip file from a file tree that contains symbolic links:
     - Use strategy `PRESERVE` and verify that the zip task fails with an appropriate error message.
     - Use strategy `FOLLOW` and verify that the zip is created.

## Story: User declares that symbolic links should be preserved when copying a file tree to a tar file

Add support for preserving symbolic links when copying to a tar file.

1. Introduce `SymbolicLinkVisitDetails` that extends `FileCopyDetails` and adds a `getTarget()` method.
2. Use this in the tar file copy action to create the symbolic link tar entry.
3. Introduce `RegularFileVisitDetails` and `DirectoryVisitDetails` types that extend `FileCopyDetails`.

## Story: Symbolic links are followed when copying from a tar file

Implement `FileTree.visit(action, SymlinkStrategy.FOLLOW) for tar file entries.

### Test cases

- Visit a tar file tree that contains symbolic links.
     - Use `copyTo()` to create a copy of the tree. Verify that that symbolic links are followed.
- Copy a tar file tree that contains symbolic links to the file system and verify that symbolic links are followed.

## Story: Preserve symbolic links when running under Java 5 and Java 6

1. Add support for reading links to the native-platform toolkit.
2. Add a native-platform backed `FileSystem` implementation.

## Story: Convenience for visiting all file copy elements

1. Add `CopySpec.eachElement()` which is called for all elements - files, directories and symlinks.
2. Add `CopySpec.eachDirectory()` which is called for all directory elements.
3. Add `CopySpec.eachSymbolicLink()` which is called for all symbolic link elements.

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
