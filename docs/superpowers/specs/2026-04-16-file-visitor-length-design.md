# Enrich FilePropertyVisitor with File Size Information

**Date:** 2026-04-16
**Status:** Approved

## Problem

The DV plugin uses `SnapshotTaskInputsBuildOperationType` and `SnapshotTransformInputsBuildOperationType` to get a list of resolved artifacts. The result provides a `FilePropertyVisitor` that allows traversing found files. The DV plugin is interested in regular files on the top level (just below a root, not inside a directory). Currently, `FilePropertyVisitor.VisitState` exposes the file name, path, and content hash, but not the file size.

The file size is available internally in `RegularFileSnapshot.getMetadata().getLength()`, and is already present at the point where the visit state is populated (`BaseFilePropertyVisitState.visitEntry()`), but is not wired through to the public API.

## Approach

Add `getLength()` directly to `FilePropertyVisitor.VisitState`. This is the simplest option with a single new method, no new types, and minimal code change. It is consistent with `getHashBytes()` being on the same interface.

Alternatives considered:
- **Default method with sentinel (-1):** Misleading because the default would not actually be returned on old Gradle at runtime (throws `NoSuchMethodError` instead). Creates a false sense of graceful degradation.
- **New sub-interface (`FileVisitState extends VisitState`):** Adds interface complexity for a single field. The `instanceof` check triggers `NoClassDefFoundError` on old Gradle, so the type-level distinction doesn't provide practical benefit over a direct method addition.

## API Change

Add to `FilePropertyVisitor.VisitState` (in `platforms/enterprise/enterprise-operations`):

```java
/**
 * Returns the length in bytes of the last visited file.
 * <p>
 * Must not be called when the last visited location was a directory.
 *
 * @since 9.x
 */
long getLength();
```

This mirrors the contract of `getHashBytes()` -- only valid during `file()` visits, not during directory visits.

Update the Javadoc on `FilePropertyVisitor.file(VisitState)` to list `getLength()` among the callable methods.

Update the Javadoc on `SnapshotTaskInputsBuildOperationType.Result.InputFilePropertyVisitor.file(VisitState)` similarly, since its `VisitState` inherits from `FilePropertyVisitor.VisitState`.

## Implementation

All changes are in `BaseFilePropertyVisitState` (in `subprojects/core`), the shared base for both the task and transform visitor state implementations:

1. Add a `long length` field alongside the existing `path`, `name`, `hash` fields.
2. In `visitEntry()`, after setting `path`/`name`/`hash`, extract the length:
   ```java
   this.length = snapshot instanceof RegularFileSnapshot
       ? ((RegularFileSnapshot) snapshot).getMetadata().getLength()
       : 0;
   ```
   Directories are filtered out at the top of `visitEntry()`. Non-directory snapshots that reach this point are expected to be `RegularFileSnapshot` instances, but `MissingFileSnapshot` is also a leaf type that passes the directory filter, so the `instanceof` guard is added for safety.
3. Implement `getLength()` returning the `length` field.
4. In `DirectoryVisitState` (nested class), implement `getLength()` throwing `UnsupportedOperationException`, following the same pattern as `getHashBytes()`.

No changes needed to `FilePropertyVisitState` or `SnapshotTaskInputsResultFilePropertyVisitState` -- they delegate to `BaseFilePropertyVisitState` which handles everything.

## Files Changed

| File | Change |
|------|--------|
| `platforms/enterprise/enterprise-operations/src/main/java/org/gradle/operations/execution/FilePropertyVisitor.java` | Add `getLength()` to `VisitState`; update `file()` Javadoc |
| `platforms/enterprise/enterprise-operations/src/main/java/org/gradle/api/internal/tasks/SnapshotTaskInputsBuildOperationType.java` | Update `InputFilePropertyVisitor.file()` Javadoc |
| `subprojects/core/src/main/java/org/gradle/api/internal/tasks/BaseFilePropertyVisitState.java` | Add `length` field, `getLength()` impl, populate in `visitEntry()`, add to `DirectoryVisitState` |

## Backward Compatibility

Backward compatibility is required in both directions.

**Old DV plugin + New Gradle:** The old plugin never calls `getLength()`. The method exists but is unused. No breakage.

**New DV plugin + Old Gradle:** Calling `state.getLength()` throws `NoSuchMethodError` at runtime because old Gradle's `VisitState` interface does not have the method. The DV plugin must guard against this:

```java
// DV plugin side (illustrative, not part of this change)
try {
    long length = state.getLength();
} catch (NoSuchMethodError e) {
    long length = new File(state.getPath()).length();
}
```

## Testing

Add or extend an existing integration test in the `SnapshotTaskInputsBuildOperationType` test suite to verify that `getLength()` returns the correct file size during `file()` visits.
