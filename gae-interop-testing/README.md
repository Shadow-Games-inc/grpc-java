=====================================

This directory contains interop tests that runs in Google App Engine
as gRPC clients.

Prerequisites
==========================

- Install the Google Cloud SDK and ensure that `gcloud` is in the path
- Set up an [App Engine app](https://appengine.google.com) with your
  choice of a PROJECT_ID.
- Associate your `gcloud` environment with your app:
  ```bash
  # Log into Google Cloud
  $ gcloud auth login

  # Associate this codebase with a GAE project
  $ gcloud config set project PROJECT_ID
  ```

Running the tests in GAE
==========================

