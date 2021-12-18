
The hostname example is a Hello World server whose response includes its
hostname. It also supports health and reflection services. This makes it a good
server to test infrastructure, like load balancing.

The example requires grpc-java to already be built. You are strongly encouraged
to check out a git release tag, since there will already be a build of grpc
available. Otherwise you must follow [COMPILING](../../COMPILING.md).

### Build the example

1. Build the hello-world example client. See [the examples README](../README.md)

2. Build this server. From the `grpc-java/examples/examples-hostname` directory:
```
$ ../gradlew installDist
```

This creates the script `build/install/hostname-server/bin/hostname-server` that
runs the example.

To run the hostname example, run:

```
$ ./build/install/hostname/bin/hostname-server
```

And in a different terminal window run the hello-world client:

```
$ ../build/install/examples/bin/hello-world-client
