package org.gradle.sample.app.data;

import java.util.List;
import java.util.ArrayList;

public class Message {
    private String message;
    private List<String> receivers = new ArrayList<>();

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getReceivers() {
        return receivers;
    }

    public void setReceivers(List<String> receivers) {
        this.receivers = receivers;
    }

    @Override
    public String toString() {
        return "Message{message='" + message + '\'' +
            ", receivers=" + receivers + '}';
    }
}
