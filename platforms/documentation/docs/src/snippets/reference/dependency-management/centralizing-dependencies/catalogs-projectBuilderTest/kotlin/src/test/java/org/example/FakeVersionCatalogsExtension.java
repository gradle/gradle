package org.example;

// tag::fake_version_catalogs_extension[]
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class FakeVersionCatalogsExtension implements VersionCatalogsExtension {

    private final Map<String, VersionCatalog> catalogs = new LinkedHashMap<>();

    public FakeVersionCatalogsExtension(VersionCatalog... catalogs) {
        for (VersionCatalog catalog : catalogs) {
            this.catalogs.put(catalog.getName(), catalog);
        }
    }

    @Override
    public Optional<VersionCatalog> find(String name) {
        return Optional.ofNullable(catalogs.get(name));
    }

    @Override
    public Set<String> getCatalogNames() {
        return catalogs.keySet();
    }

    @Override
    public Iterator<VersionCatalog> iterator() {
        return catalogs.values().iterator();
    }
}
// end::fake_version_catalogs_extension[]
