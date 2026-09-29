package org.example;

// tag::fake_version_catalog[]
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.plugin.use.PluginDependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A minimal, hand-written {@link VersionCatalog} fake for unit tests.
 * Only library lookups are implemented -- extend the other find*() methods
 * the same way if your plugin also reads bundles, versions or plugins.
 */
public class FakeVersionCatalog implements VersionCatalog {

    private final String name;
    private final Map<String, Provider<MinimalExternalModuleDependency>> libraries;

    public FakeVersionCatalog(String name, Map<String, Provider<MinimalExternalModuleDependency>> libraries) {
        this.name = name;
        this.libraries = libraries;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Optional<Provider<MinimalExternalModuleDependency>> findLibrary(String alias) {
        return Optional.ofNullable(libraries.get(alias));
    }

    @Override
    public Optional<Provider<ExternalModuleDependencyBundle>> findBundle(String alias) {
        return Optional.empty();
    }

    @Override
    public Optional<VersionConstraint> findVersion(String alias) {
        return Optional.empty();
    }

    @Override
    public Optional<Provider<PluginDependency>> findPlugin(String alias) {
        return Optional.empty();
    }

    @Override
    public List<String> getLibraryAliases() {
        return new ArrayList<>(libraries.keySet());
    }

    @Override
    public List<String> getBundleAliases() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getVersionAliases() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getPluginAliases() {
        return Collections.emptyList();
    }
}
// end::fake_version_catalog[]
