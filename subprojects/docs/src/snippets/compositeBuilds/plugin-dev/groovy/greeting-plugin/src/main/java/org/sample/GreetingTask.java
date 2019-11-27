package org.sample;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public class GreetingTask extends DefaultTask {
    private String who = "mate";

    @Input
    public String getWho() {
        return who;
    }

    public void setWho(String who) {
        this.who = who;
    }

    @TaskAction
    public void greet() {
        System.out.println("Hi " + who + "!!!");
    }
}
