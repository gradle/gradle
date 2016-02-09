/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.jvm;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.internal.nativeintegration.filesystem.FileCanonicalizer;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.*;

public class JavaInstallationsDirLocator implements JavaInstallationsDirLocatorStrategy {

    public static JavaInstallationsDirLocator withDefaultStrategies(ExecActionFactory execFactory, List<JavaInstallationsDirLocatorStrategy> strategies) {
        NativeServices nativeServices = NativeServices.getInstance();
        ImmutableList.Builder<JavaInstallationsDirLocatorStrategy> builder = ImmutableList.<JavaInstallationsDirLocatorStrategy>builder().addAll(strategies);
        if (OperatingSystem.current().isWindows()) {
            builder.add(new WindowsJavaInstallationsDirLocatorStrategy(nativeServices.get(WindowsRegistry.class)));
        } else if (OperatingSystem.current().isMacOsX()) {
            builder.add(new OsxJavaInstallationsDirLocatorStrategy(execFactory));
        } else if (OperatingSystem.current().isUnix()) {
            builder.add(new UnixJavaInstallationsDirLocatorStrategy(nativeServices.get(FileCanonicalizer.class)));
        }
        return new JavaInstallationsDirLocator(builder.build());
    }

    public static JavaInstallationsDirLocator withDefaultStrategies(ExecActionFactory execFactory) {
        return withDefaultStrategies(execFactory, Collections.<JavaInstallationsDirLocatorStrategy>emptyList());
    }

    private final List<? extends JavaInstallationsDirLocatorStrategy> strategies;
    private final Supplier<Set<File>> cache;

    public JavaInstallationsDirLocator(List<? extends JavaInstallationsDirLocatorStrategy> strategies) {
        this.strategies = strategies;
        cache = Suppliers.memoize(javaInstallationsDirsSupplier());
    }

    public Set<File> findJavaInstallationsDirs() {
        return cache.get();
    }

    private Supplier<Set<File>> javaInstallationsDirsSupplier() {
        return new Supplier<Set<File>>() {
            @Override
            public Set<File> get() {
                Set<File> result = new LinkedHashSet<File>();
                for (JavaInstallationsDirLocatorStrategy strategy : strategies) {
                    result.addAll(strategy.findJavaInstallationsDirs());
                }
                return result;
            }
        };
    }
}
