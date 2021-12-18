from a maven repository. But if you need the latest SNAPSHOT binaries you will need to follow
[COMPILING](../COMPILING.md) to build these.

Please follow the [steps](./README.md#to-build-the-examples) to build the examples. The build creates
the script `google-auth-client` in the `build/install/examples/bin/` directory which can be
used to run this example.

The example uses [Google PubSub gRPC API](https://cloud.google.com/pubsub/docs/reference/rpc/) to get a list
of PubSub topics for a project. You will need to perform the following steps to get the example to work.
Wherever possible, the required UI links or `gcloud` shell commands are mentioned.

1. Create or use an existing [Google Cloud](https://cloud.google.com) account. In your account, you may need
to enable features to exercise this example and this may cost some money.

2. Use an existing project, or [create a project](https://pantheon.corp.google.com/projectcreate),
say `Google Auth Pubsub example`. Note down the project ID of this project - say `xyz123` for this example.
Use the project drop-down from the top or use the cloud shell command
```
gcloud projects list
```
to get the project ID.

3. Unless already enabled, [enable the Cloud Pub/Sub API for your project](https://console.developers.google.com/apis/api/pubsub.googleapis.com/overview)
