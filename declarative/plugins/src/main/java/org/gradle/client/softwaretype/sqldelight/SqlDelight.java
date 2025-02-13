package org.gradle.client.softwaretype.sqldelight;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public abstract class SqlDelight {
    public abstract NamedDomainObjectContainer<Database> getDatabases();
}
