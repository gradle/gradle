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

package org.gradle.internal.reflection.access;

import java.lang.instrument.Instrumentation;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/// Opens packages through [Instrumentation].
class DefaultModuleOpener implements ModuleOpener {

    private final Instrumentation instrumentation;

    private final Module targetModule = DefaultModuleOpener.class.getModule();

    /// Keyed by source [Module]. We require the source to live in [ModuleLayer#boot()],
    /// the JDK-defined layer created at JVM startup. Boot-layer modules are never unloaded - they
    /// share the lifespan of the daemon by construction - so holding strong references here does not
    /// pin any [ModuleLayer] or [ClassLoader] that could otherwise have been collected.
    ///
    /// The inner set holds the packages already opened to [#targetModule]. The set is also thread-safe.
    private final ConcurrentMap<Module, Set<String>> openedPackagesByModule = new ConcurrentHashMap<>();

    DefaultModuleOpener(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    @Override
    public void openPackageOf(Class<?> reflectedClass) {
        Module sourceModule = reflectedClass.getModule();
        if (sourceModule == targetModule || !sourceModule.isNamed()) {
            // Skip anything that is in unnamed module.
            // Normally, unless build logic constructed its own ModuleLayer, this is everything except JDK itself.
            // This will have to be revisited if Gradle adopts `-modulepath`.
            return;
        }
        // The class is under module protection. It has to be in an open package to allow deep reflection.
        // A _module_ can open a _package_ to another _module_.
        // As a shortcut, we're opening to the module this class is defined in.
        String packageName = reflectedClass.getPackageName();
        Set<String> openedPackages = openedPackagesByModule.get(sourceModule);
        if (openedPackages != null && openedPackages.contains(packageName)) {
            // We've opened the package already.
            return;
        }
        // It is possible that two threads will reach there for the same package.
        // This is benign, as redefine module is idempotent, the check above is
        // just an optimization for the hot path.
        ModuleLayer sourceModuleLayer = sourceModule.getLayer();
        if (sourceModuleLayer != ModuleLayer.boot()) {
            if (sourceModuleLayer == null) {
                // This is a class defined in a dynamic module, most likely a Proxy.
                // We shouldn't be there at all, as Proxies are handled by a custom codec.
                // Probably not worth a potential hard failure.
                assert false : "Tried to open the module of class %s that has no layer".formatted(reflectedClass);
                return;
            }
            // This class is defined outside of the JDK's boot module.
            // As Gradle doesn't use JPMS, this means some user code is creating modules on its own.
            // We don't support that: the primary client of this method, CC serialization, doesn't know about modules and cannot rebuild them.
            // It is very unlikely previous versions of Gradle were able to load these classes correctly.
            throw new IllegalStateException(
                String.format(
                    "Cannot open package '%s' in module '%s' defined in layer '%s'. Only boot layer modules are supported",
                    packageName, sourceModule.getName(), sourceModuleLayer
                )
            );
        }
        instrumentation.redefineModule(
            sourceModule,
            Set.of(), // extraReads
            Map.of(), // extraExports
            Map.of(packageName, Set.of(targetModule)), // extraOpens
            Set.of(), // extraUses
            Map.of() // extraProvides
        );
        openedPackagesByModule
            .computeIfAbsent(sourceModule, m -> ConcurrentHashMap.newKeySet())
            .add(packageName);
    }
}
