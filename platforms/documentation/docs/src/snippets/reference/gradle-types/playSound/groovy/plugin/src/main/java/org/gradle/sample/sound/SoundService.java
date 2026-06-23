package org.gradle.sample.sound;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.io.File;

public abstract class SoundService implements BuildService<BuildServiceParameters.None> {
    public void playSoundFile(File path) {
        System.out.println("Playing sound " + path.getName());
    }
}
