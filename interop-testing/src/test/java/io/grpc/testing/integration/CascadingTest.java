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

public class CascadingTest {
  private CountDownLatch observedCancellations;
  @Before
    futureStub = TestServiceGrpc.newFutureStub(channel);
    server.shutdownNow();
   * Test {@link Context} cancellation propagates from the first node in the call chain all the way
  public void testCascadingCancellationViaOuterContextCancellation() throws Exception {
    Future<SimpleResponse> future;
    } finally {
    try {
      Status status = Status.fromThrowable(ex);
      // Should have observed 2 cancellations responses from downstream servers
      }
    }
   * Test that cancellation via call cancellation propagates down the call.
    receivedCancellations = new CountDownLatch(3);
    chainReady.get(5, TimeUnit.SECONDS);
      fail("Expected number of cancellations not observed by clients");
    }
   * RPC triggers cancellation of all of its children.
    // All nodes (15) except one edge of the tree (4) will be cancelled.
      // Use response size limit to control tree nodeCount.
    } catch (StatusRuntimeException sre) {
      assertEquals(Status.Code.ABORTED, status.getCode());
        fail("Expected number of cancellations not observed by clients");
  }
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
