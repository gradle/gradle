# Build process and worker thread robustness
When a build process is stared, there are several threads which communicate via Gradle's messaging infrastructure (`MessagingServices`):

1. The worker thread(s) in the build process that start the worker, send some messages, wait for something to happen and wait for the worker process to finish.
2. A couple of threads in the build process that send and receive on the connection to the worker.
3. The main thread in the worker process, that runs the worker action that sets up the workers, waits for something to happen and exits the process.
4. A couple of threads in the worker process that send and receive on the connection to the build process.
5. The worker thread(s) in the worker process that do the work in response to messages, and maybe sends some messages back.

Currently, any or all of these can fail, and at the moment we don't deal with this particularly well. We want to add some robustness at this level first, before adding robustness down deeper.

# Stories

# failures in the worker thread, in the worker process, gracefully degrade
This is the most likely point of failure.
- When a worker fails, it should no longer be used.
- when all the workers fail, a 'workers failed' message is sent back to the build process, tear down the connection, unblock the main thread and
exit the worker process. The worker threads in the build process would be notified of this failure and can clean up.

### Implementation
### Test cases

# Move responsibility for cleaning up the connection and worker process to infrastructure
Move this responsibility to the infrastructure so that the worker action is not involved in cleanup. This way the robustness can live in a single place rather than being
reimplemented in each of the worker actions.

### Implementation
### Test cases

# Handle failures to send or receive messages on the worker process.
Dealing with failures on the worker side of the connection...
On failure to receive or send on the worker process side, we could just tear down the connection and worker process, perhaps waiting for the worker threads to finish up first.
This would look similar to the previous failure to the build process. The worker threads would be notified that the worker is gone and can clean up.
Same for when the worker process crashes (eg kill -9, segfault, Runtime.halt(), System.exit()). This would also deal with the build process going missing.

### Implementation
### Test cases

# Robustness for stuck worker processes
forcefully kill the worker process after some timeout on shutdown.

### Implementation
### Test cases

# Robustness for failures on the build process side of the connection
Notify the worker threads know and they can initiate a shutdown.

### Implementation
### Test cases
