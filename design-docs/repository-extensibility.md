Our current repository implementations are not particularly extensible, composable, or pluggable. In some cases users must resort
to using a custom Ivy DependencyResolver in order to handle a specific repository. This spec attempts to address these shortcomings.

# Use cases

- Enable caching for a file repository
- Resolve files from a googlecode project
- Resolve files from a Nuget repository
- Resolve files from a GitHub repository
- Resolve files from a SourceForge project
- Use a custom module version listing protocol, such as the Artifactory API

# Stories

## Allow caching for a slow file-backed repository (GRADLE-1964)

Currently, the only way to enable caching for file-backed repository (eg when backed by a slow NFS share) is to set local attribute to false for a FileSystemResolver.
We need a way to prevent very slow resolution in these cases:

* Add caching for file-backed repositories. Once per build, check for updates to a given resource and copy into cache. Should use the same
up-to-date mechanism used for incremental build.
* Detect remote file systems and don't check for updates for these file systems when running with `--offline`.
    * Use `native-platform` to detect remote file systems and cache these automatically.

See [GRADLE-1964](http://issues.gradle.org/browse/GRADLE-1964)

## Detect servers that do not correctly handle HTTP HEAD requests

Provide an automatic workaround for https://code.google.com/p/support/issues/detail?id=660.

- Hopefully this could detect bad servers due something unique in the HTTP HEAD response.
- Or we could maintain a list of servers that we know are badly behaved
- Or we could probe by sending both GET and HEAD requests periodically

One issue with probing is that this behaviour is non-deterministic. If the file has been recently retrieved, then HEAD will not return 404.

## Resolve artifact files from a GoogleCode repository

In this story, we add the ability to resolve files from a Google Code repository:

* 'googlecode' repository type
* version listing via googlecode api
* no meta-data handling (file-only repository)

See: https://github.com/Ullink/gradle-repositories-plugin/blob/master/src/main/groovy/com/ullink/RepositoriesPlugin.groovy#L122

## Resolve artifact files from a Nuget repository

In this story, we simply add the ability to resolve files in a Nuget repository:

* 'nuget' repository type
* version listing via Nuget api
* No meta-data handling (file-only repository)

See: https://github.com/Ullink/gradle-repositories-plugin/blob/master/src/main/groovy/com/ullink/RepositoriesPlugin.groovy#L150

## Resolve artifact files from a GitHub repository

## Allow meta-data file format to be specified for repositories

This story would permit the metadata file format (ivy/pom/etc) to be specified for a googlecode, nuget or other repository.


