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

package org.gradle.launcher.daemon.management;

import java.io.File;
import java.util.List;

/**
 * Every Gradle daemon under one daemon base directory, of every version.
 *
 * <p>This is the entry point for a tool that manages daemons: an IDE showing what is running, a command
 * line asking for everything to stop, a script waiting for a build to finish. It replaces reaching into
 * the internals of whichever Gradle version happens to be at hand, which is what such tools do today and
 * why they break whenever those internals move.
 */
public interface ManagedDaemons {

    /**
     * Every daemon that is currently registered, of every Gradle version, whether or not it still answers.
     *
     * <p>Daemons that died without cleaning up are included, since a caller stopping daemons wants them
     * gone and a caller listing them wants to know they are there. Ask {@link ManagedDaemon#isAlive()} to
     * tell the two apart.
     */
    List<ManagedDaemon> all();

    /**
     * Starts an idle daemon for the given project, ready to run its builds.
     *
     * <p>The Gradle version comes from the project itself, so this starts the daemon the project would
     * have used anyway. Starting a daemon for a version other than the one this library ships with needs
     * that version's distribution to be resolved and launched, which is the wrapper's job and is not done
     * here.
     *
     * @param projectDir the project the daemon should serve
     * @throws UnsupportedDaemonVersionException when the project asks for a Gradle version this library
     * cannot start
     */
    ManagedDaemon start(File projectDir) throws UnsupportedDaemonVersionException;
}
