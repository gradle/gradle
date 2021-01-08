package org.myorg;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Nested;

abstract public class SiteExtension {

    abstract public RegularFileProperty getOutputDir();

    @Nested
    abstract public CustomData getCustomData();

    public void customData(Action<? super CustomData> action) {
        action.execute(getCustomData());
    }
}
