/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.messaging.remote.internal;

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.messaging.dispatch.AsyncDispatch;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.DispatchFailureHandler;
import org.gradle.messaging.dispatch.QueuingDispatch;
import org.gradle.messaging.remote.internal.protocol.EndOfStreamEvent;
import org.gradle.messaging.remote.internal.protocol.RoutableMessage;
import org.gradle.messaging.remote.internal.protocol.RouteAvailableMessage;
import org.gradle.messaging.remote.internal.protocol.RouteUnavailableMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public class Router implements Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Router.class);
    private final AsyncDispatch<Runnable> workQueue;
    private final Group localConnections = new LocalGroup();
    private final Group remoteConnections = new RemoteGroup();
    private final DispatchFailureHandler<? super Message> failureHandler;

    public Router(Executor executor, DispatchFailureHandler<? super Message> failureHandler) {
        this.failureHandler = failureHandler;
        localConnections.peer = remoteConnections;
        remoteConnections.peer = localConnections;
        workQueue = new AsyncDispatch<Runnable>(executor);
        workQueue.dispatchTo(new Dispatch<Runnable>() {
            public void dispatch(Runnable message) {
                message.run();
            }
        });
    }

    public AsyncConnection<Message> createLocalConnection() {
        return new Endpoint(localConnections);
    }

    public AsyncConnection<Message> createRemoteConnection() {
        return new Endpoint(remoteConnections);
    }

    public void stop() {
        workQueue.stop();
    }

    private class Endpoint implements AsyncConnection<Message> {
        private QueuingDispatch<Message> handler = new QueuingDispatch<Message>();
        final Group owner;
        final Group peerGroup;
        final Set<Route> routes = new HashSet<Route>();

        protected Endpoint(Group owner) {
            this.owner = owner;
            this.peerGroup = owner.peer;
            owner.addEndpoint(this);
        }

        public void dispatchTo(final Dispatch<? super Message> handler) {
            workQueue.dispatch(new Runnable() {
                public void run() {
                    Endpoint.this.handler.dispatchTo(handler);
                }
            });
        }

        public void dispatch(final Message message) {
            workQueue.dispatch(new Runnable() {
                public void run() {
                    try {
                        if (message instanceof RouteAvailableMessage) {
                            RouteAvailableMessage routeAvailableMessage = (RouteAvailableMessage) message;
                            LOGGER.debug("Received route available. Message: {}", routeAvailableMessage);
                            Route route = new Route(routeAvailableMessage.getId(), Endpoint.this, routeAvailableMessage);
                            routes.add(route);
                            owner.addRoute(route);
                        } else if (message instanceof RouteUnavailableMessage) {
                            RouteUnavailableMessage routeUnavailableMessage = (RouteUnavailableMessage) message;
                            LOGGER.debug("Received route unavailable. Message: {}", routeUnavailableMessage);
                            Route route = owner.removeRoute(routeUnavailableMessage.getId());
                            routes.remove(route);
                        } else if (message instanceof RoutableMessage) {
                            RoutableMessage routableMessage = (RoutableMessage) message;
                            peerGroup.send(routableMessage.getDestination(), message);
                        } else if (message instanceof EndOfStreamEvent) {
                            for (Route route : routes) {
                                LOGGER.debug("Removing route {} due to end of stream.", route.id);
                                owner.removeRoute(route.id);
                            }
                            owner.removeEndpoint(Endpoint.this);
                            // Ping the message back
                            dispatchIncoming(message);
                        } else {
                            throw new UnsupportedOperationException(String.format("Received message which cannot be routed: %s.", message));
                        }
                    } catch (Throwable throwable) {
                        failureHandler.dispatchFailed(message, throwable);
                    }
                }
            });
        }

        void dispatchIncoming(Message message) {
            handler.dispatch(message);
        }
    }

    private static class Route {
        final Object id;
        final RouteAvailableMessage announcement;
        final Endpoint destination;
        final Set<Route> targets = new HashSet<Route>();

        private Route(Object id, Endpoint destination, RouteAvailableMessage announcement) {
            this.id = id;
            this.destination = destination;
            this.announcement = announcement;
        }

        public void connectTo(Route target) {
            targets.add(target);
            target.destination.dispatchIncoming((Message) announcement);
        }
    }

    private static class Group {
        private final Map<Object, Route> routes = new HashMap<Object, Route>();
        private final Set<Endpoint> endpoints = new HashSet<Endpoint>();
        Group peer;

        public void addEndpoint(Endpoint endpoint) {
            endpoints.add(endpoint);
        }

        public void addRoute(Route route) {
            routes.put(route.id, route);
        }

        public Route removeRoute(Object routeId) {
            return routes.remove(routeId);
        }

        public void send(Object routeId, Message message) {
            routes.get(routeId).destination.dispatchIncoming(message);
        }

        public void removeEndpoint(Endpoint endpoint) {
            endpoints.remove(endpoint);
        }
    }

    private static class LocalGroup extends Group {
        @Override
        public void addRoute(Route route) {
            super.addRoute(route);
            for (Endpoint endpoint : peer.endpoints) {
                endpoint.dispatchIncoming((Message) route.announcement);
            }
            for (Route targetRoute : peer.routes.values()) {
                if (route.announcement.acceptIncoming(targetRoute.announcement)) {
                    targetRoute.connectTo(route);
                }
            }
        }

        @Override
        public Route removeRoute(Object routeId) {
            Route route = super.removeRoute(routeId);
            Message unavailableMessage = (Message) route.announcement.getUnavailableMessage();
            for (Endpoint endpoint : peer.endpoints) {
                endpoint.dispatchIncoming(unavailableMessage);
            }
            for (Route targetRoute : peer.routes.values()) {
                targetRoute.targets.remove(route);
            }
            return route;
        }

        @Override
        public void removeEndpoint(Endpoint endpoint) {
            super.removeEndpoint(endpoint);
            for (Route route : peer.routes.values()) {
                route.targets.removeAll(endpoint.routes);
            }
        }
    }

    private static class RemoteGroup extends Group {
        @Override
        public void addEndpoint(Endpoint endpoint) {
            super.addEndpoint(endpoint);
            for (Route route : peer.routes.values()) {
                endpoint.dispatchIncoming((Message) route.announcement);
            }
        }

        @Override
        public void addRoute(Route route) {
            super.addRoute(route);
            for (Route targetRoute : peer.routes.values()) {
                if (targetRoute.announcement.acceptIncoming(route.announcement)) {
                    route.connectTo(targetRoute);
                }
            }
        }

        @Override
        public Route removeRoute(Object routeId) {
            Route route = super.removeRoute(routeId);
            for (Route target : route.targets) {
                Message unavailableMessage = (Message) route.announcement.getUnavailableMessage();
                target.destination.dispatchIncoming(unavailableMessage);
            }
            route.targets.clear();
            return route;
        }
    }
}
