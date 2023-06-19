/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.ClassLoaderWorkerSpec;
import org.gradle.workers.ProcessWorkerSpec;

import javax.inject.Inject;
import java.util.Map;
import java.util.regex.Pattern;

public class DefaultProcessWorkerSpec extends DefaultClassLoaderWorkerSpec implements ProcessWorkerSpec, ClassLoaderWorkerSpec {
    /**
     * Environment variables inherited automatically on Unix systems.
     *
     * See <a href="https://www.gnu.org/software/gettext/manual/html_node/Locale-Environment-Variables.html">Locale Environment Variables for gettext</a>
     */
    private static final Pattern INHERITED_UNIX_ENVIRONMENT = Pattern.compile("(LANG|LANGUAGE|LC_.*)");

    protected final JavaForkOptions forkOptions;

    @Inject
    public DefaultProcessWorkerSpec(JavaForkOptions forkOptions, ObjectFactory objectFactory) {
        super(objectFactory);
        this.forkOptions = forkOptions;
        this.forkOptions.setEnvironment(sanitizeEnvironment(forkOptions));
    }

    /**
     * Inherit as little as possible from the parent process' environment.
     *
     * On Unix systems we need to pass a few environment variables to make sure
     * the file system is accessed with the right encoding.
     */
    private static Map<String, Object> sanitizeEnvironment(JavaForkOptions forkOptions) {
        if (!OperatingSystem.current().isUnix()) {
            return ImmutableMap.of();
        }
        return forkOptions.getEnvironment().entrySet().stream()
            .filter(entry -> INHERITED_UNIX_ENVIRONMENT.matcher(entry.getKey()).matches())
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public JavaForkOptions getForkOptions() {
        return forkOptions;
    }

    @Override
    public void forkOptions(Action<? super JavaForkOptions> forkOptionsAction) {
        forkOptionsAction.execute(forkOptions);
    }
}
