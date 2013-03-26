package org.gradle.test.fixtures.server.http

import org.mortbay.jetty.Server
import org.mortbay.jetty.webapp.WebAppContext

class ServletContainer {
    private final Server webServer = new Server(0)
    private final warFile

    ServletContainer(File warFile) {
        this.warFile = warFile
    }

    int getPort() {
        webServer.connectors[0].localPort
    }

    void start() {
        def context = new WebAppContext()
        context.war = warFile
        webServer.addHandler(context)
        webServer.start()
    }

    void stop() {
        webServer.stop()
    }
}
