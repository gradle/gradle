package com.acme;

import dagger.Module;
import dagger.Provides;

@Module
public class Sensors {
    @Provides static HeatSensor provideHeater() {
        return new DefaultHeatSensor();
    }
}
