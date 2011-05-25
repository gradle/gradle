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

import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.dispatch.AsyncDispatch;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.DispatchFailureHandler;
import org.gradle.messaging.dispatch.QueuingDispatch;
import org.gradle.messaging.remote.internal.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executor;

public class Router implements Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Router.class);
    private final AsyncDispatch<Runnable> workQueue;
    private final Set<Endpoint> localConnections = new HashSet<Endpoint>();
    private final Set<Endpoint> remoteConnections = new HashSet<Endpoint>();
    private final Map<Object, Route> allRoutes = new HashMap<Object, Route>();
    private final DispatchFailureHandler<? super Message> failureHandler;

    public Router(Executor executor, DispatchFailureHandler<? super Message> failureHandler) {
        this.failureHandler = failureHandler;
        workQueue = new AsyncDispatch<Runnable>(executor);
        workQueue.dispatchTo(new Dispatch<Runnable>() {
            public void dispatch(Runnable message) {
                message.run();
            }
        });
    }

    public AsyncConnection<Message> createLocalConnection() {
        return new LocalConnection();
    }

    public AsyncConnection<Message> createRemoteConnection() {
        return new RemoteConnection();
    }

    public void stop() {
        workQueue.stop();
    }

    private abstract class Endpoint {
        private QueuingDispatch<Message> handler = new QueuingDispatch<Message>();
        private final Set<Endpoint> broadcastTo;
        private final Set<Route> routes = new HashSet<Route>();

        protected Endpoint(final Set<Endpoint> connections, final Set<Endpoint> broadcastTo) {
            this.broadcastTo = broadcastTo;
            workQueue.dispatch(new Runnable() {
                public void run() {
                    connections.add(Endpoint.this);
                    for (Route route : allRoutes.values()) {
                        if (broadcastTo.contains(route.destination)) {
                            receive(route.announcement);
                        }
                    }
                }
            });
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
                        Message routingTarget = message;
                        if (message instanceof ChannelMessage) {
                            ChannelMessage channelMessage = (ChannelMessage) message;
                            routingTarget = (Message) channelMessage.getPayload();
                        }

                        if (routingTarget instanceof RouteAvailableMessage) {
                            RouteAvailableMessage routeAvailableMessage = (RouteAvailableMessage) routingTarget;
                            LOGGER.debug("Received route available. Message: {}", routeAvailableMessage);
                            Route route = new Route(routeAvailableMessage.getId(), Endpoint.this, message);
                            routes.add(route);
                            allRoutes.put(routeAvailableMessage.getId(), route);
                        } else if (routingTarget instanceof RouteUnavailableMessage) {
                            RouteUnavailableMessage routeUnavailableMessage = (RouteUnavailableMessage) routingTarget;
                            LOGGER.debug("Received route unavailable. Message: {}", routeUnavailableMessage);
                            Route route = allRoutes.remove(routeUnavailableMessage.getId());
                            routes.remove(route);
                        }
                        if (routingTarget instanceof RoutableMessage) {
                            RoutableMessage routableMessage = (RoutableMessage) routingTarget;
                            Object destination = routableMessage.getDestination();
                            if (destination == null) {
                                broadcast(message);
                            } else {
                                allRoutes.get(destination).destination.receive(message);
                            }
                        } else if (routingTarget instanceof EndOfStreamEvent) {
                            for (Route route : routes) {
                                LOGGER.debug("Removing route {} due to end of stream.", route.id);
                                Message unavailableMessage = route.getUnavailableMessage();
                                broadcast(unavailableMessage);
                                allRoutes.remove(route.id);
                            }
                            localConnections.remove(Endpoint.this);
                            remoteConnections.remove(Endpoint.this);
                            routes.clear();
                            // Ping the message back
                            receive(message);
                        } else {
                            throw new UnsupportedOperationException(String.format("Received message which cannot be routed: %s.", message));
                        }
                    } catch (Throwable throwable) {
                        failureHandler.dispatchFailed(message, throwable);
                    }
                }
            });
        }

        void receive(Message message) {
            handler.dispatch(message);
        }

        void broadcast(Message message) {
            for (Endpoint remoteConnection : broadcastTo) {
                remoteConnection.receive(message);
            }
        }
    }

    private static class Route {
        final Object id;
        final ChannelMessage announcement;
        final Endpoint destination;

        private Route(Object id, Endpoint destination, Message announcement) {
            this.id = id;
            this.destination = destination;
            this.announcement = (ChannelMessage) announcement;
        }

        public Message getUnavailableMessage() {
            RouteAvailableMessage routeAvailableMessage = (RouteAvailableMessage) announcement.getPayload();
            return announcement.withPayload(routeAvailableMessage.getUnavailableMessage());
        }
    }

    private class LocalConnection extends Endpoint implements AsyncConnection<Message> {
        private LocalConnection() {
            super(localConnections, remoteConnections);
        }
    }

    private class RemoteConnection extends Endpoint implements AsyncConnection<Message> {
        private RemoteConnection() {
            super(remoteConnections, localConnections);
        }
    }
}
