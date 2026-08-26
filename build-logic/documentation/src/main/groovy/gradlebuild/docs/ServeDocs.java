/*
 * Copyright 2024 the original author or authors.
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

package gradlebuild.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.deployment.internal.DeploymentRegistry;
import org.gradle.internal.deployment.JavaApplicationHandle;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Optional;

/**
 * Serves the given directory with a simple HTTP server.
 */
@DisableCachingByDefault(because = "This task starts a HTTP server and should not be cached.")
public abstract class ServeDocs extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    protected abstract DirectoryProperty getDocsDirectory();

    @Input
    @Option(option = "port", description = "Port for the local HTTP server")
    public abstract Property<Integer> getPort();

    @Nested
    protected abstract Property<JavaLauncher> getJavaLauncher();

    @TaskAction
    public void startApplication() {
        int port = getPort().get();
        verifyPortAvailable(port);
        getLogger().lifecycle("serving docs at http://localhost:" + port);
        DeploymentRegistry registry = getDeploymentRegistry();
        JavaApplicationHandle handle = registry.get(getPath(), JavaApplicationHandle.class);
        if (handle == null) {
            JavaExecHandleBuilder builder = getExecActionFactory().newJavaExec();
            builder.setExecutable(getJavaLauncher().get().getExecutablePath().getAsFile());
            builder.getMainModule().set("jdk.httpserver");
            builder.setStandardOutput(System.out);
            builder.setErrorOutput(System.err);
            builder.setArgs(Arrays.asList("-p", port, "-d", getDocsDirectory().get().getAsFile().getAbsolutePath()));
            registry.start(getPath(), DeploymentRegistry.ChangeBehavior.RESTART, JavaApplicationHandle.class, builder);
        }
    }

    /**
     * Probes the requested port synchronously so a bind failure surfaces as a task error rather
     * than a post-BUILD-SUCCESSFUL stderr line from the DeploymentRegistry's background handle.
     */
    private void verifyPortAvailable(int port) {
        if (!isPortAvailable(port)) {
            String suggestion = findFreePort(port)
                .map(free -> String.format(", e.g.:%n  ./gradlew %s --port %d", getPath(), free))
                .orElse(".");
            throw new GradleException(String.format("Port %d is already in use.%nTry running with a different port%s", port, suggestion));
        }
    }

    /**
     * Probes port availability using the exact bind target the JDK HTTP server will use:
     * loopback address with SO_REUSEADDR disabled. Binding to the wildcard address or leaving
     * SO_REUSEADDR at its Unix default (true) would let this probe silently coexist with an
     * already-bound loopback socket on BSD-derived systems, incorrectly reporting the port as free.
     */
    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Suggests a free port when the requested one is unavailable. Tries {@code 8100}, {@code 8101},
     * {@code 8102} first so the printed command reads naturally, then falls back to an OS-picked
     * ephemeral port. Returns empty if no free port could be found.
     */
    private Optional<Integer> findFreePort(int requested) {
        for (int candidate : new int[]{8100, 8101, 8102}) {
            if (candidate != requested && isPortAvailable(candidate)) {
                return Optional.of(candidate);
            }
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            return Optional.of(socket.getLocalPort());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Inject
    protected abstract DeploymentRegistry getDeploymentRegistry();

    @Inject
    protected abstract JavaExecHandleFactory getExecActionFactory();
}
