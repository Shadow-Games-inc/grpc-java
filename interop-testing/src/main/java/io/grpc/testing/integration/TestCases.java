/*
 * Copyright 2016 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.testing.integration;

import com.google.common.base.Preconditions;

/**
 * Enum of interop test cases.
 */
public enum TestCases {
  EMPTY_UNARY("empty (zero bytes) request and response"),
  CACHEABLE_UNARY("cacheable unary rpc sent using GET"),
  LARGE_UNARY("single request and (large) response"),
  CLIENT_COMPRESSED_UNARY("client compressed unary request"),
  CLIENT_COMPRESSED_UNARY_NOPROBE(
      "client compressed unary request (skip initial feature-probing request)"),
  SERVER_COMPRESSED_UNARY("server compressed unary response"),
  CLIENT_STREAMING("request streaming with single response"),
  CLIENT_COMPRESSED_STREAMING("client per-message compression on stream"),
  CLIENT_COMPRESSED_STREAMING_NOPROBE(
      "client per-message compression on stream (skip initial feature-probing request)"),
