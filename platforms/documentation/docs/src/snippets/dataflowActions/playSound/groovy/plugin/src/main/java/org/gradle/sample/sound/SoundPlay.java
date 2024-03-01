package org.gradle.sample.sound;

import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Input;

import java.io.File;

public abstract class SoundPlay implements FlowAction<SoundPlay.Parameters> {
    interface Parameters extends FlowParameters {
        @ServiceReference // <1>
        Property<SoundService> getSoundService();

        @Input // <2>
        Property<File> getMediaFile();
    }

    @Override
    public void execute(Parameters parameters) {
        parameters.getSoundService().get().playSoundFile(parameters.getMediaFile().get());
    }
}
