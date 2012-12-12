Offer ways to deal with duplicate entries when creating Zip/Tar/Jar/War/Ear archives.

# Use cases

Archives can easily end up containing duplicate entries without the user intending this. For example, when the output directories
for classes and resources are reconfigured to point to the same directory (which can, for example, be necessary for tests involving JPA entities),
the resulting Jar archive will contain each file twice. This is not only undesirable, but can easily go unnoticed because no warning is issued.

Users should have an easy way to prevent duplicate file (path)s from being added to an archive. Nevertheless, the (rare?) case where duplicates
are desired should still be supported. One rationale for supporting duplicates is that a Jar archive should be able to represent any class path,
which may of course contain duplicates. For certain resource files (e.g. service descriptors) it is even essential that the class path can contain
duplicates. A concrete example where duplicate (resource) paths may be desirable is a fat Jar.

# Implementation plan

## "Ignore duplicates" strategy for archive tasks

There should be a configuration option on archive tasks that prevents duplicate file paths from being added to the archive.

### User visible changes

A new property on archive tasks. Could be a boolean flag or an enum.

### Sad day cases

Can't think of any.

### Test coverage

Add multiple files/directories with the same archive path, and make sure only the first one gets added.

### Implementation approach

Remember which file paths have already been added to the archive, and only add a file if its path isn't already taken.

# Open issues

Potential other strategies that could be supported:

* Fail on duplicates
* Warn on duplicates

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