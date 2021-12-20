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

import static java.util.concurrent.Executors.newFixedThreadPool;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.integration.Messages.Payload;
import io.grpc.testing.integration.Messages.SimpleRequest;
import io.grpc.testing.integration.Messages.SimpleResponse;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client application for the {@link TestServiceGrpc.TestServiceImplBase} that runs through a series
 * of HTTP/2 interop tests. The tests are designed to simulate incorrect behavior on the part of the
 * server. Some of the test cases require server-side checks and do not have assertions within the
 * client code.
 */
public final class Http2Client {
  private static final Logger logger = Logger.getLogger(Http2Client.class.getName());

  /**
   * The main application allowing this client to be launched from the command line.
   */
  public static void main(String[] args) throws Exception {
    final Http2Client client = new Http2Client();
    client.parseArgs(args);
    client.setUp();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        try {
          client.shutdown();
        } catch (Exception e) {
          logger.log(Level.SEVERE, e.getMessage(), e);
        }
      }
    });

    try {
      client.run();
    } finally {
      client.shutdown();
    }
  }

  private String serverHost = "localhost";
  private int serverPort = 8080;
  private String testCase = Http2TestCases.RST_AFTER_DATA.name();

  private Tester tester = new Tester();
  private ListeningExecutorService threadpool;

  ManagedChannel channel;
  TestServiceGrpc.TestServiceBlockingStub blockingStub;
  TestServiceGrpc.TestServiceStub asyncStub;

  private void parseArgs(String[] args) {
    boolean usage = false;
    for (String arg : args) {
      if (!arg.startsWith("--")) {
        System.err.println("All arguments must start with '--': " + arg);
        usage = true;
        break;
      }
      String[] parts = arg.substring(2).split("=", 2);
      String key = parts[0];
      if ("help".equals(key)) {
        usage = true;
        break;
      }
      if (parts.length != 2) {
        System.err.println("All arguments must be of the form --arg=value");
        usage = true;
        break;
      }
      String value = parts[1];
      if ("server_host".equals(key)) {
        serverHost = value;
      } else if ("server_port".equals(key)) {
        serverPort = Integer.parseInt(value);
      } else if ("test_case".equals(key)) {
        testCase = value;
      } else {
        System.err.println("Unknown argument: " + key);
        usage = true;
        break;
      }
    }
    if (usage) {
      Http2Client c = new Http2Client();
      System.out.println(
          "Usage: [ARGS...]"
              + "\n"
              + "\n  --server_host=HOST          Server to connect to. Default " + c.serverHost
              + "\n  --server_port=PORT          Port to connect to. Default " + c.serverPort
              + "\n  --test_case=TESTCASE        Test case to run. Default " + c.testCase
              + "\n    Valid options:"
              + validTestCasesHelpText()
      );
      System.exit(1);
    }
  }

