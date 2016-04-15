With the current implementation of clean/delete, symlinks are traversed when deleting files. This is the behavior of ant, but it is the opposite behavior of `rm -rf` and is dangerous. If something links to a system folder, Gradle will try to delete the system files.

This is a breaking change to how Gradle functions, but it was decided that it's worth the change. More details about the discussion are located on the [dev mailing list](https://groups.google.com/forum/#!topic/gradle-dev/2HIji78xT3I).

For users that want to follow symlinks, they will be able to use `ant.delete()`.

# Use cases

We want to make clean and delete safer when a folder is symlinked into the build directory or delete is called.

# Implementation plan

This section lists the stories that make up the implementation plan for the feature. The stories should be listed in priority order.

## Story 1: Update delete to not follow symlinks.

If a user were to accidentally put a symlink from the buildDir to the HOME directory, when `gradle clean` is run, the entire home directory will be deleted.

### User visible changes

When a user runs `gradle clean`, has a Delete task, or calls `project.delete()` and there is a symlink in the folder to be deleted, the symlink will be removed without trying to delete the files though the symlink.

The Delete task will have a new property added called `followSymlinks` which will default to false.

### Implementation

There are two different ways that we can determine if a file is a symlink. We can either use the Apache IO that does a string comparison to determine if the paths are the same or use native-platform's `Files`.

I think that using native-platform would be a better option to reduce the amount of time it would take to check for long paths. When native-platform isn't available, the delete should fall back to Apache IO's implementation of symlink checking which is Java 6 compatible.

When looping over the contents of directory to delete and a File is a symlink, then the link should be removed and the source should be left unchanged.

### Test coverage

- When normal file detected, it should be deleted.
- When a directory is detected, it and it's contents should be deleted.
- When a file or directory is a symlink outside of the directory to be deleted, it should be kept, but the link should be removed.
- When a file or directory is a symlink inside the directory to be deleted, it should be removed along with the link.

# Open issues
