# File Visitor Length Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `getLength()` to `FilePropertyVisitor.VisitState` so the DV plugin can retrieve file sizes when traversing resolved artifacts via the snapshot build operations.

**Architecture:** Single new method on the existing `VisitState` interface, implemented in `BaseFilePropertyVisitState` by extracting the length from `RegularFileSnapshot.getMetadata()` during the snapshot tree walk. The `DirectoryVisitState` inner class throws `UnsupportedOperationException`, matching the existing pattern for `getHashBytes()`.

**Tech Stack:** Java, Groovy (tests), Spock framework

**Spec:** `docs/superpowers/specs/2026-04-16-file-visitor-length-design.md`

---

### Task 1: Add `getLength()` to the `FilePropertyVisitor.VisitState` interface

**Files:**
- Modify: `platforms/enterprise/enterprise-operations/src/main/java/org/gradle/operations/execution/FilePropertyVisitor.java:84-125`

- [ ] **Step 1: Add the `getLength()` method to the `VisitState` interface**

In `FilePropertyVisitor.java`, add the new method after `getHashBytes()` (after line 124):

```java
        /**
         * Returns the length in bytes of the last visited file.
         * <p>
         * Must not be called when the last visited location was a directory.
         *
         * @since 9.2
         */
        long getLength();
```

- [ ] **Step 2: Update the `file()` Javadoc to mention `getLength()`**

In `FilePropertyVisitor.java`, update the Javadoc for `file(VisitState)` (lines 56-60) from:

```java
    /**
     * Called when visiting a non-directory file.
     * <p>
     * {@link VisitState#getName()}, {@link VisitState#getPath()} and {@link VisitState#getHashBytes()} may be called during.
     */
```

to:

```java
    /**
     * Called when visiting a non-directory file.
     * <p>
     * {@link VisitState#getName()}, {@link VisitState#getPath()}, {@link VisitState#getHashBytes()} and {@link VisitState#getLength()} may be called during.
     */
```

---

### Task 2: Update `InputFilePropertyVisitor.file()` Javadoc

**Files:**
- Modify: `platforms/enterprise/enterprise-operations/src/main/java/org/gradle/api/internal/tasks/SnapshotTaskInputsBuildOperationType.java:129-134`

- [ ] **Step 1: Update the `file()` Javadoc in `InputFilePropertyVisitor`**

In `SnapshotTaskInputsBuildOperationType.java`, update the Javadoc for `InputFilePropertyVisitor.file(VisitState)` (lines 129-134) from:

```java
            /**
             * Called when visiting a non-directory file.
             * <p>
             * {@link VisitState#getName()}, {@link VisitState#getPath()} and {@link VisitState#getHashBytes()} may be called during.
             */
```

to:

```java
            /**
             * Called when visiting a non-directory file.
             * <p>
             * {@link VisitState#getName()}, {@link VisitState#getPath()}, {@link VisitState#getHashBytes()} and {@link VisitState#getLength()} may be called during.
             */
```

---

### Task 3: Implement `getLength()` in `BaseFilePropertyVisitState`

**Files:**
- Modify: `subprojects/core/src/main/java/org/gradle/api/internal/tasks/BaseFilePropertyVisitState.java:37-212`

- [ ] **Step 1: Add the `RegularFileSnapshot` import**

Add this import after the existing `DirectorySnapshot` import (line 24):

```java
import org.gradle.internal.snapshot.RegularFileSnapshot;
```

- [ ] **Step 2: Add the `length` field**

Add `long length;` after the `int depth;` field (after line 47):

```java
    long length;
```

- [ ] **Step 3: Implement `getLength()`**

Add the `getLength()` override after the existing `getHashBytes()` method (after line 93):

```java
    @Override
    public long getLength() {
        return length;
    }
```

- [ ] **Step 4: Populate the `length` field in `visitEntry()`**

In the `visitEntry()` method, after line 131 (`this.hash = fingerprint.getNormalizedContentHash();`), add:

```java
        this.length = snapshot instanceof RegularFileSnapshot
            ? ((RegularFileSnapshot) snapshot).getMetadata().getLength()
            : 0;
```

- [ ] **Step 5: Add `getLength()` to `DirectoryVisitState`**

In the `DirectoryVisitState` inner class, add a `getLength()` override after the `getHashBytes()` method (after line 195). This follows the same pattern as `getHashBytes()`:

```java
        @Override
        public long getLength() {
            throw new UnsupportedOperationException("Cannot query length for directories");
        }
```

- [ ] **Step 6: Verify compilation**

Run:
```bash
./gradlew :core:compileJava :enterprise-operations:compileJava
```

Expected: BUILD SUCCESSFUL

---

### Task 4: Write test for `getLength()` in the visitor

**Files:**
- Modify: `subprojects/core/src/testFixtures/groovy/org/gradle/api/internal/tasks/AbstractSnapshotInputsBuildOperationResultTest.groovy:39-241`

- [ ] **Step 1: Add the `RegularFileSnapshot` import**

Add this import to `AbstractSnapshotInputsBuildOperationResultTest.groovy` (after the existing snapshot import on line 29):

```groovy
import org.gradle.internal.snapshot.RegularFileSnapshot
```

- [ ] **Step 2: Write the test method**

Add the following test method at the end of the class (before the closing `}`):

```groovy
    @Requires(OsTestPreconditions.NotWindows)
    def "file visitor provides file length"() {
        given:
        def visitor = createMockVisitor()
        def inputFileProperty = Mock(InputFilePropertySpec) {
            getDirectorySensitivity() >> IGNORE_DIRECTORIES
            getLineEndingNormalization() >> NORMALIZE_LINE_ENDINGS
            getNormalizer() >> InputNormalizer.ABSOLUTE_PATH
            getPropertyName() >> 'foo'
        }
        def fileOneSnapshot = new RegularFileSnapshot(
            '/foo/one.txt', 'one.txt',
            TestHashCodes.hashCodeFrom(123),
            file(0, 1024, DIRECT)
        )
        def fileTwoSnapshot = new RegularFileSnapshot(
            '/foo/sub/two.txt', 'two.txt',
            TestHashCodes.hashCodeFrom(234),
            file(0, 2048, DIRECT)
        )
        def snapshots = directory('/foo', [
            fileOneSnapshot,
            directory('/foo/sub', [
                fileTwoSnapshot
            ])
        ])
        def beforeExecutionState = Mock(BeforeExecutionState) {
            getInputFileProperties() >> ImmutableSortedMap.of('foo',
                Mock(CurrentFileCollectionFingerprint) {
                    getHash() >> TestHashCodes.hashCodeFrom(345)
                    getFingerprints() >> [
                        '/foo/one.txt': new DefaultFileSystemLocationFingerprint('/foo/one.txt', FileType.RegularFile, TestHashCodes.hashCodeFrom(123)),
                        '/foo/sub/two.txt': new DefaultFileSystemLocationFingerprint('/foo/sub/two.txt', FileType.RegularFile, TestHashCodes.hashCodeFrom(234)),
                    ]
                    getSnapshot() >> snapshots
                }
            )
        }
        def cachingState = CachingState.enabled(Mock(BuildCacheKey), beforeExecutionState)
        def buildOpResult = createSnapshotInputsBuildOperationResult(
            cachingState,
            [inputFileProperty] as Set
        )

        when:
        buildOpResult.visitInputFileProperties(visitor)

        then:
        1 * visitor.file { it.path == '/foo/one.txt' && it.length == 1024 }

        then:
        1 * visitor.file { it.path == '/foo/sub/two.txt' && it.length == 2048 }
    }
```

Note: this test creates `RegularFileSnapshot` instances directly with known lengths (1024 and 2048 bytes) instead of using the `regularFile()` helper, which uses random lengths.

- [ ] **Step 3: Run the tests**

Run:
```bash
./gradlew :core:test --tests '*SnapshotTaskInputsBuildOperationResultTest' :dependency-management:test --tests '*SnapshotTransformInputsBuildOperationResultTest'
```

Expected: both test classes pass (the new `"file visitor provides file length"` test runs in both concrete subclasses).

---

### Task 5: Commit

- [ ] **Step 1: Commit all changes**

```bash
git add \
  platforms/enterprise/enterprise-operations/src/main/java/org/gradle/operations/execution/FilePropertyVisitor.java \
  platforms/enterprise/enterprise-operations/src/main/java/org/gradle/api/internal/tasks/SnapshotTaskInputsBuildOperationType.java \
  subprojects/core/src/main/java/org/gradle/api/internal/tasks/BaseFilePropertyVisitState.java \
  subprojects/core/src/testFixtures/groovy/org/gradle/api/internal/tasks/AbstractSnapshotInputsBuildOperationResultTest.groovy

git commit -m "Add getLength() to FilePropertyVisitor.VisitState

Enrich the file property visitor with file size information so that
consumers (e.g. the DV plugin) can retrieve file lengths when
traversing resolved artifacts via SnapshotTaskInputsBuildOperationType
and SnapshotTransformInputsBuildOperationType.

The length is extracted from RegularFileSnapshot metadata during the
snapshot tree walk in BaseFilePropertyVisitState.visitEntry()."
```
