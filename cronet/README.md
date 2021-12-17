gRPC Cronet Transport
========================

**EXPERIMENTAL:**  *gRPC's Cronet transport is an experimental API. Its stability
depends on upstream Cronet's implementation, which involves some experimental features.*

This code enables using the [Chromium networking stack
(Cronet)](https://chromium.googlesource.com/chromium/src/+/master/components/cronet)
as the transport layer for gRPC on Android. This lets your Android app make
RPCs using the same networking stack as used in the Chrome browser.

Some advantages of using Cronet with gRPC:

* Bundles an OpenSSL implementation, enabling TLS connections even on older
  versions of Android without additional configuration
* Robust to Android network connectivity changes
* Support for [QUIC](https://www.chromium.org/quic)

Since gRPC's 1.24 release, the `grpc-cronet` package provides access to the 
`CronetChannelBuilder` class. Cronet jars are available on Google's Maven repository. 
See the example app at https://github.com/GoogleChrome/cronet-sample/blob/master/README.md.

