/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.tooling.internal.consumer.daemon;

import org.gradle.tooling.daemon.DaemonManagement;
import org.gradle.tooling.daemon.GradleDaemon;
import org.gradle.tooling.daemon.StopResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class DefaultDaemonManagement implements DaemonManagement {

    private final GradleVersionDaemonRegistries registries;
    private final RegistryReader registryReader;
    private final DaemonStopper stopper;

    public DefaultDaemonManagement(File gradleUserHomeDir) {
        this.registries = new GradleVersionDaemonRegistries(defaultGradleUserHome(gradleUserHomeDir));
        this.registryReader = new RegistryReader();
        this.stopper = new DaemonStopper();
    }

    private static File defaultGradleUserHome(File configured) {
        if (configured != null) {
            return configured;
        }
        String fromEnv = System.getenv("GRADLE_USER_HOME");
        if (fromEnv != null) {
            return new File(fromEnv);
        }
        return new File(System.getProperty("user.home"), ".gradle");
    }

    @Override
    public List<GradleDaemon> listDaemons() {
        List<GradleDaemon> result = new ArrayList<>();
        for (DaemonEntry entry : listEntries()) {
            result.add(new DefaultGradleDaemon(entry.info, entry.gradleVersion));
        }
        return result;
    }

    @Override
    public List<GradleDaemon> listDaemons(String gradleVersion) {
        List<GradleDaemon> result = new ArrayList<>();
        GradleVersionDaemonRegistries.Entry entry = registries.findForVersion(gradleVersion);
        if (entry != null) {
            for (DaemonInfoView info : registryReader.read(entry.registryFile, entry.gradleVersion)) {
                result.add(new DefaultGradleDaemon(info, entry.gradleVersion));
            }
        }
        return result;
    }

    @Override
    public void stopAll() {
        for (DaemonEntry entry : listEntries()) {
            stopper.stop(entry.info);
        }
    }

    @Override
    public StopResult stop(GradleDaemon daemon) {
        return stopByUid(daemon.getUid());
    }

    @Override
    public StopResult stopByPid(long pid) {
        for (DaemonEntry entry : listEntries()) {
            if (entry.info.context.pid != null && entry.info.context.pid == pid) {
                return stopper.stop(entry.info);
            }
        }
        return StopResult.NOT_FOUND;
    }

    @Override
    public StopResult stopByUid(String uid) {
        for (DaemonEntry entry : listEntries()) {
            if (uid.equals(entry.info.context.uid)) {
                return stopper.stop(entry.info);
            }
        }
        return StopResult.NOT_FOUND;
    }

    private List<DaemonEntry> listEntries() {
        List<DaemonEntry> result = new ArrayList<>();
        for (GradleVersionDaemonRegistries.Entry entry : registries.findAll()) {
            for (DaemonInfoView info : registryReader.read(entry.registryFile, entry.gradleVersion)) {
                result.add(new DaemonEntry(info, entry.gradleVersion));
            }
        }
        return result;
    }

    private static final class DaemonEntry {
        final DaemonInfoView info;
        final String gradleVersion;

        DaemonEntry(DaemonInfoView info, String gradleVersion) {
            this.info = info;
            this.gradleVersion = gradleVersion;
        }
    }
}
