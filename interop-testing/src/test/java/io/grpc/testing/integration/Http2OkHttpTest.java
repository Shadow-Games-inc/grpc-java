/*
 * Copyright 2014 The gRPC Authors
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Throwables;
import com.squareup.okhttp.ConnectionSpec;
import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ServerBuilder;
import io.grpc.ServerCredentials;
import io.grpc.TlsChannelCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.internal.GrpcUtil;
import io.grpc.internal.testing.StreamRecorder;
import io.grpc.internal.testing.TestUtils;
import io.grpc.netty.InternalNettyServerBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.okhttp.InternalOkHttpChannelBuilder;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.okhttp.internal.Platform;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.integration.EmptyProtos.Empty;
import java.io.IOException;
