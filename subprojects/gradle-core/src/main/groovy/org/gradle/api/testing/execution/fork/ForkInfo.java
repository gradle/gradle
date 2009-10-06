package org.gradle.api.testing.execution.fork;

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyForkInfo;
import org.gradle.util.ThreadUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Tom Eyckmans
 */
public class ForkInfo {
    private final int id;
    private final Pipeline pipeline;
    private ForkPolicyForkInfo policyInfo;
    private boolean restarting;

    private List<ForkInfoListener> listeners = new CopyOnWriteArrayList<ForkInfoListener>();

    public ForkInfo(int id, Pipeline pipeline) {
        this.id = id;
        this.pipeline = pipeline;
    }

    public int getId() {
        return id;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public ForkPolicyForkInfo getForkPolicyInfo() {
        return policyInfo;
    }

    public void setPolicyInfo(ForkPolicyForkInfo policyInfo) {
        this.policyInfo = policyInfo;
    }

    public boolean isRestarting() {
        return restarting;
    }

    public void setRestarting(boolean restarting) {
        this.restarting = restarting;
    }

    public void addListener(ForkInfoListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ForkInfoListener listener) {
        listeners.remove(listener);
    }

    public void started() {
        final List<ForkInfoListener> currentListeners = new ArrayList<ForkInfoListener>(listeners);
        for (final ForkInfoListener currentListener : currentListeners) {
            ThreadUtils.join(ThreadUtils.run(new Runnable() {
                public void run() {
                    currentListener.started(id);
                }
            }));
        }
    }

    public void stopped(final Throwable cause) {
        final List<ForkInfoListener> currentListeners = new ArrayList<ForkInfoListener>(listeners);
        for (final ForkInfoListener currentListener : currentListeners) {
            ThreadUtils.join(ThreadUtils.run(new Runnable() {
                public void run() {
                    currentListener.stopped(id, cause);
                }
            }));
        }
    }
}
