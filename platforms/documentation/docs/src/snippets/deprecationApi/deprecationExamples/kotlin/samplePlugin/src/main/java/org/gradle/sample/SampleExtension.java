package org.gradle.sample;

import org.gradle.api.problems.Problems;

import javax.inject.Inject;

public class SampleExtension {

    private final Problems problems;
    private String message;

    @Inject
    public SampleExtension(Problems problems) {
        this.problems = problems;
        problems.getDeprecationReporter().deprecate(
            "The extension 'org.gradle.sample.SampleExtension' is deprecated.",
            spec -> spec
                .removedInVersion(3, 0, 0, null)
                .because("The extension is no longer supported")
        );
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        problems.getDeprecationReporter().deprecateMethod(
            this.getClass(),
            "getMessage()",
            spec -> spec
                .removedInVersion(2, 0, 0, null)
                .replacedBy("A new object `message { }`, which is a better representation of the message")
        );
        this.message = message;
    }
}
