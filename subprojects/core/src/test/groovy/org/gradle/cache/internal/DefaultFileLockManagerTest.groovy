package org.gradle.cache.internal

import spock.lang.Specification
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import java.nio.channels.OverlappingFileLockException
import org.gradle.cache.internal.FileLockManager.LockMode

/**
 * @author: Szczepan Faber, created at: 8/30/11
 */
class DefaultFileLockManagerTest extends Specification {
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder()
    def manager = new DefaultFileLockManager()

    def "cannot lock twice in single process"() {
        when:
        lock(FileLockManager.LockMode.Exclusive);
        lock(FileLockManager.LockMode.Exclusive);

        then:
        thrown(OverlappingFileLockException)
    }

    def "cannot lock twice in single process for mixed modes"() {
        when:
        lock(FileLockManager.LockMode.Exclusive);
        lock(FileLockManager.LockMode.Shared);

        then:
        thrown(OverlappingFileLockException)
    }

    def "cannot lock twice in single process for shared mode"() {
        when:
        lock(FileLockManager.LockMode.Shared);
        lock(FileLockManager.LockMode.Shared);

        then:
        thrown(OverlappingFileLockException)
    }

    private FileLock lock(LockMode lockMode) {
        return manager.lock(tmpDir.file("state.bin"), lockMode, "foo")
    }
}
