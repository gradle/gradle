package org.gradle.launcher.daemon.server

import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.registry.DaemonDir
import spock.lang.Ignore
import spock.lang.Specification

class DaemonRegistryUnavailableExpirationStrategyTest extends Specification {
    final Daemon daemon = Mock(Daemon)
    final DaemonContext daemonContext = Mock(DaemonContext)
    final DaemonDir daemonDir = Mock(DaemonDir)

    def "daemon should expire when registry file is unreachable"() {
        given:
        DaemonRegistryUnavailableExpirationStrategy expirationStrategy = new DaemonRegistryUnavailableExpirationStrategy()

        when:
        1 * daemon.getDaemonContext() >> { daemonContext }
        1 * daemonContext.getDaemonRegistryDir() >> { new File("BOGUS") }
        boolean daemonShouldExpire = expirationStrategy.shouldExpire(daemon)

        then:
        daemonShouldExpire
    }

    // FIXME(ew): create TempDir and registry.bin file
    @Ignore
    def "daemon should not expire given readable registry"() {
        given:
        DaemonRegistryUnavailableExpirationStrategy expirationStrategy = new DaemonRegistryUnavailableExpirationStrategy()

        when:
        1 * daemon.getDaemonContext() >> { daemonContext }
        1 * daemonContext.getDaemonRegistryDir() >> { new File("foo") }

        then:
        !expirationStrategy.shouldExpire(daemon)
    }
}
