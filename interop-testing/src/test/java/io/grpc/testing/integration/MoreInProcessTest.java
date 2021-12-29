/*
 * Copyright 2015 The gRPC Authors
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.integration.Messages.StreamingInputCallRequest;
import io.grpc.testing.integration.Messages.StreamingInputCallResponse;
import io.grpc.testing.integration.TestServiceGrpc.TestServiceImplBase;
import io.grpc.util.MutableHandlerRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link io.grpc.inprocess}.
 * Test more corner usecases, client not playing by the rules, server not playing by the rules, etc.
 */
@RunWith(JUnit4.class)
public class MoreInProcessTest {
  private static final String UNIQUE_SERVER_NAME =
      "in-process server for " + MoreInProcessTest.class;

  // Use a fairly large timeout to work around classloading slowness.
  @Rule
  public final Timeout globalTimeout = new Timeout(10, TimeUnit.SECONDS);
  // use a mutable service registry for later registering the service impl for each test case.
  private final MutableHandlerRegistry serviceRegistry = new MutableHandlerRegistry();
  private final Server inProcessServer = InProcessServerBuilder.forName(UNIQUE_SERVER_NAME)
      .fallbackHandlerRegistry(serviceRegistry).directExecutor().build();
  private final ManagedChannel inProcessChannel =
      InProcessChannelBuilder.forName(UNIQUE_SERVER_NAME).directExecutor().build();

  @Before
  public void setUp() throws Exception {
    inProcessServer.start();
  }

  @After
  public void tearDown() throws Exception {
    inProcessChannel.shutdown();
    inProcessServer.shutdown();
    assertTrue(inProcessChannel.awaitTermination(900, TimeUnit.MILLISECONDS));
    assertTrue(inProcessServer.awaitTermination(900, TimeUnit.MILLISECONDS));
  }

  @Test
  public void asyncClientStreaming_serverResponsePriorToRequest() throws Exception {
    // implement a service
    final StreamingInputCallResponse fakeResponse =
        StreamingInputCallResponse.newBuilder().setAggregatedPayloadSize(100).build();
    TestServiceImplBase clientStreamingImpl = new TestServiceImplBase() {
      @Override
      public StreamObserver<StreamingInputCallRequest> streamingInputCall(
          StreamObserver<StreamingInputCallResponse> responseObserver) {
        // send response directly
