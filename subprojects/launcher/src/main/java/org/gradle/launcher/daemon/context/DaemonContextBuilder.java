/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.context;

import org.gradle.util.Jvm;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.Factory;

import java.io.File;
import java.io.IOException;

/**
 * Builds a daemon context, reflecting the current environment.
 * <p>
 * The builder itself has properties for different context values, that allow you to override
 * what would be set based on the environment. This is primarily to aid in testing.
 */
public class DaemonContextBuilder implements Factory<DaemonContext> {

    private final Jvm jvm = Jvm.current();

    private File javaHome;

    public DaemonContextBuilder() {
        try {
            javaHome = jvm.getJavaHome().getCanonicalFile();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to canonicalise JAVA_HOME '" + jvm.getJavaHome(), e);
        }
    }

    public File getJavaHome() {
        return javaHome;
    }

    public void setJavaHome(File javaHome) {
        this.javaHome = javaHome;
    }

    /**
     * Creates a new daemon context, based on the current state of this builder.
     */
    public DaemonContext create() {
        return new DefaultDaemonContext(javaHome);
    }
}