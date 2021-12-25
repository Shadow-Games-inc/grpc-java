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

import static com.google.common.truth.Truth.assertAbout;
import static io.grpc.testing.DeadlineSubject.deadline;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.util.concurrent.SettableFuture;
import io.grpc.Context;
import io.grpc.Context.CancellableContext;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.integration.Messages.SimpleRequest;
import io.grpc.testing.integration.Messages.SimpleResponse;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Integration test for various forms of cancellation and deadline propagation.
 */
@RunWith(JUnit4.class)
public class CascadingTest {

  @Mock
  TestServiceGrpc.TestServiceImplBase service;
  private ManagedChannel channel;
  private Server server;
  private CountDownLatch observedCancellations;
  private CountDownLatch receivedCancellations;
  private TestServiceGrpc.TestServiceBlockingStub blockingStub;
  private TestServiceGrpc.TestServiceStub asyncStub;
  private TestServiceGrpc.TestServiceFutureStub futureStub;
  private ExecutorService otherWork;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    // Use a cached thread pool as we need a thread for each blocked call
    otherWork = Executors.newCachedThreadPool();
    channel = InProcessChannelBuilder.forName("channel").executor(otherWork).build();
    blockingStub = TestServiceGrpc.newBlockingStub(channel);
    asyncStub = TestServiceGrpc.newStub(channel);
    futureStub = TestServiceGrpc.newFutureStub(channel);
  }

  @After
  public void tearDown() {
    channel.shutdownNow();
    server.shutdownNow();
    otherWork.shutdownNow();
  }

  /**
   * Test {@link Context} cancellation propagates from the first node in the call chain all the way
   * to the last.
   */
  @Test
  public void testCascadingCancellationViaOuterContextCancellation() throws Exception {
    observedCancellations = new CountDownLatch(2);
    receivedCancellations = new CountDownLatch(3);
    Future<?> chainReady = startChainingServer(3);
    CancellableContext context = Context.current().withCancellation();
    Future<SimpleResponse> future;
    Context prevContext = context.attach();
    try {
      future = futureStub.unaryCall(SimpleRequest.getDefaultInstance());
    } finally {
      context.detach(prevContext);
    }
    chainReady.get(5, TimeUnit.SECONDS);

    context.cancel(null);
    try {
      future.get(5, TimeUnit.SECONDS);
      fail("Expected cancellation");
    } catch (ExecutionException ex) {
      Status status = Status.fromThrowable(ex);
      assertEquals(Status.Code.CANCELLED, status.getCode());

      // Should have observed 2 cancellations responses from downstream servers
      if (!observedCancellations.await(5, TimeUnit.SECONDS)) {
        fail("Expected number of cancellations not observed by clients");
      }
      if (!receivedCancellations.await(5, TimeUnit.SECONDS)) {
        fail("Expected number of cancellations to be received by servers not observed");
      }
    }
  }

  /**
   * Test that cancellation via call cancellation propagates down the call.
   */
  @Test
  public void testCascadingCancellationViaRpcCancel() throws Exception {
    observedCancellations = new CountDownLatch(2);
    receivedCancellations = new CountDownLatch(3);
    Future<?> chainReady = startChainingServer(3);
    Future<SimpleResponse> future = futureStub.unaryCall(SimpleRequest.getDefaultInstance());
    chainReady.get(5, TimeUnit.SECONDS);

    future.cancel(true);
    assertTrue(future.isCancelled());
    if (!observedCancellations.await(5, TimeUnit.SECONDS)) {
      fail("Expected number of cancellations not observed by clients");
    }
    if (!receivedCancellations.await(5, TimeUnit.SECONDS)) {
      fail("Expected number of cancellations to be received by servers not observed");
    }
  }

  /**
   * Test that when RPC cancellation propagates up a call chain, the cancellation of the parent
   * RPC triggers cancellation of all of its children.
   */
  @Test
  public void testCascadingCancellationViaLeafFailure() throws Exception {
    // All nodes (15) except one edge of the tree (4) will be cancelled.
    observedCancellations = new CountDownLatch(11);
    receivedCancellations = new CountDownLatch(11);
    startCallTreeServer(3);
    try {
      // Use response size limit to control tree nodeCount.
      blockingStub.unaryCall(Messages.SimpleRequest.newBuilder().setResponseSize(3).build());
      fail("Expected abort");
    } catch (StatusRuntimeException sre) {
      // Wait for the workers to finish
      Status status = sre.getStatus();
      // Outermost caller observes ABORTED propagating up from the failing leaf,
      // The descendant RPCs are cancelled so they receive CANCELLED.
      assertEquals(Status.Code.ABORTED, status.getCode());

      if (!observedCancellations.await(5, TimeUnit.SECONDS)) {
        fail("Expected number of cancellations not observed by clients");
      }
      if (!receivedCancellations.await(5, TimeUnit.SECONDS)) {
        fail("Expected number of cancellations to be received by servers not observed");
      }
    }
  }

  @Test
  public void testDeadlinePropagation() throws Exception {
    final AtomicInteger recursionDepthRemaining = new AtomicInteger(3);
    final SettableFuture<Deadline> finalDeadline = SettableFuture.create();
    class DeadlineSaver extends TestServiceGrpc.TestServiceImplBase {
        Context.currentContextExecutor(otherWork).execute(new Runnable() {
            try {
              } else {
            } catch (Exception ex) {
          }

        .build().start();
    assertNotSame(initialDeadline, finalDeadline);
  }
  /**
    class ChainingService extends TestServiceGrpc.TestServiceImplBase {
          @Override
        if (serversReady.incrementAndGet() == depthThreshold) {
  }
