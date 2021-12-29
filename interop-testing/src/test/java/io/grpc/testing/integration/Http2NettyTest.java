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
import static org.junit.Assert.assertNotEquals;

import io.grpc.ChannelCredentials;
import io.grpc.Metadata;
import io.grpc.ServerBuilder;
import io.grpc.ServerCredentials;
import io.grpc.TlsChannelCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.internal.testing.TestUtils;
import io.grpc.netty.InternalNettyChannelBuilder;
import io.grpc.netty.InternalNettyServerBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.MetadataUtils;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for GRPC over HTTP2 using the Netty framework.
 */
@RunWith(JUnit4.class)
public class Http2NettyTest extends AbstractInteropTest {

  @Override
  protected ServerBuilder<?> getServerBuilder() {
    // Starts the server with HTTPS.
    try {
      ServerCredentials serverCreds = TlsServerCredentials.newBuilder()
          .keyManager(TestUtils.loadCert("server1.pem"), TestUtils.loadCert("server1.key"))
          .trustManager(TestUtils.loadCert("ca.pem"))
          .clientAuth(TlsServerCredentials.ClientAuth.REQUIRE)
          .build();
      NettyServerBuilder builder = NettyServerBuilder.forPort(0, serverCreds)
          .flowControlWindow(AbstractInteropTest.TEST_FLOW_CONTROL_WINDOW)
          .maxInboundMessageSize(AbstractInteropTest.MAX_MESSAGE_SIZE);
      // Disable the default census stats tracer, use testing tracer instead.
      InternalNettyServerBuilder.setStatsEnabled(builder, false);
      return builder.addStreamTracerFactory(createCustomCensusTracerFactory());
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

