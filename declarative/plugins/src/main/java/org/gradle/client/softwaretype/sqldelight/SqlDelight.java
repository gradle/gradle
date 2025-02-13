package org.gradle.client.softwaretype.sqldelight;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public interface SqlDelight {
    NamedDomainObjectContainer<Database> getDatabases();
}
