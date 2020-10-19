package org.myorg;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;

abstract public class SiteExtension {

    private final CustomData customData;

    public SiteExtension(ObjectFactory objects) {
        customData = objects.newInstance(CustomData.class);
    }

    abstract public RegularFileProperty getOutputDir();

    public CustomData getCustomData() {
        return customData;
    }

    public void customData(Action<? super CustomData> action) {
        action.execute(customData);
    }
}
