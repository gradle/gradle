package org.gradle.sample.plugin;

import java.util.List;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.DomainObjectSet;

/**
 * This is a custom tooling model. It must be assignable to {@link Model} and it must be an interface.
 */
public interface CustomModel extends Model {
    String getName();

    DomainObjectSet<String> getTasks();
}
