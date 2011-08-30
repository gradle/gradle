package org.gradle.launcher;

import org.gradle.messaging.remote.Address;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: Szczepan Faber, created at: 8/29/11
 */
public class DaemonRegistryFile implements Serializable {

    private Map<Address, DaemonStatus> statusesMap = new HashMap<Address, DaemonStatus>();

    public Collection<DaemonStatus> getDaemonStatuses() {
        return statusesMap.values();
    }

    //TODO SF model
    public Map<Address, DaemonStatus> getStatusesMap() {
        return statusesMap;
    }
}