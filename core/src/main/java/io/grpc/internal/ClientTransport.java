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

package io.grpc.internal;

import io.grpc.CallOptions;
import io.grpc.ClientStreamTracer;
import io.grpc.InternalChannelz.SocketStats;
import io.grpc.InternalInstrumented;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.util.concurrent.Executor;
import javax.annotation.concurrent.ThreadSafe;

/**
 * The client-side transport typically encapsulating a single connection to a remote
 * server. However, streams created before the client has discovered any server address may
 * eventually be issued on different connections.  All methods on the transport and its callbacks
 * are expected to execute quickly.
 */
@ThreadSafe
public interface ClientTransport extends InternalInstrumented<SocketStats> {

  /**
   * Creates a new stream for sending messages to a remote end-point.
   *
   * <p>This method returns immediately and does not wait for any validation of the request. If
   * creation fails for any reason, {@link ClientStreamListener#closed} will be called to provide
   * the error information. Any sent messages for this stream will be buffered until creation has
   * completed (either successfully or unsuccessfully).
   *
   * <p>This method is called under the {@link io.grpc.Context} of the {@link io.grpc.ClientCall}.
   *
   * @param method the descriptor of the remote method to be called for this stream.
   * @param headers to send at the beginning of the call
   * @param callOptions runtime options of the call
   * @param tracers a non-empty array of tracers. The last element in it is reserved to be set by
