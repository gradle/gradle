## Story: Restructure Gradle cache layout to give file store and metadata separate versions (DONE)

This story separates the file store and meta-data cache versions so that they can evolve separately.

The Gradle cache layout will become:

* `$gradleHome/caches/modules-${l}/` - this is the cache base directory, which includes the lock. When the locking protocol changes, the version `${l}` is changed.
* `$gradleHome/caches/modules-${l}/files-${l}.${f}` - this is the file store directory. When the file store layout changes, the version `${f}` is changed.
* `$gradleHome/caches/modules-${l}/metadata-${l}.${m}` - this is the meta-data directory, includes the artifact, dependency and module meta-data caches.
  When the meta-data format changes, the version `${m}` is changed.

Initial values: `l` = 1, `f` = 1, `m` = 27.

1. Change `CacheLockingManager.createCache()` to accept a relative path name instead of a `File`. This path should be resolved relative to the meta-data directory.
2. Add methods to `CacheLockingManager` to create the file store and module meta-data `PathKeyFileStore` implementations.

### Test coverage

- Update the existing caching test coverage for the new locations. No new coverage is required.
