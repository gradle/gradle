/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.fixtures.daemon

import org.gradle.internal.nativeintegration.filesystem.Stat
import org.gradle.internal.os.OperatingSystem
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.registry.DaemonDir
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.testfixtures.internal.NativeServicesTestFixture

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.*
import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.*

class DaemonRegistryStateProbe implements DaemonStateProbe {
    private final DaemonRegistry registry
    private final DaemonContext context

    DaemonRegistryStateProbe(DaemonRegistry registry, DaemonContext context) {
        this.context = context
        this.registry = registry
    }

    void resetToken() {
        def daemonInfo = registry.all.find { it.context.pid == context.pid }
        registry.remove(daemonInfo.address)
        registry.store(new DaemonInfo(daemonInfo.address, daemonInfo.context, "password".bytes, daemonInfo.getState()))
    }

    void assertRegistryNotWorldReadable() {
        def registryFile = new DaemonDir(context.daemonRegistryDir).registry
        if (OperatingSystem.current().isLinux() || OperatingSystem.current().isMacOsX()) {
            def stat = NativeServicesTestFixture.instance.get(Stat)
            assert stat.getUnixMode(registryFile) == 0600 // user read-write
            assert stat.getUnixMode(registryFile.parentFile) == 0700 // user read-write-execute
        }
    }

    @Override
    State getCurrentState() {
        def daemonInfo = registry.all.find { it.context.pid == context.pid }
        if (daemonInfo == null) {
            return Stopped
        }
        return daemonInfo.getState()
    }
}
