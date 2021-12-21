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

import static java.util.Arrays.asList;
import static java.util.Collections.shuffle;
import static java.util.Collections.singletonList;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.internal.testing.TestUtils;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A stress test client following the
 * <a href="https://github.com/grpc/grpc/blob/master/tools/run_tests/stress_test/STRESS_CLIENT_SPEC.md">
 * specifications</a> of the gRPC stress testing framework.
 */
public class StressTestClient {

  private static final Logger log = Logger.getLogger(StressTestClient.class.getName());

  /**
   * The main application allowing this client to be launched from the command line.
   */
  public static void main(String... args) throws Exception {
    final StressTestClient client = new StressTestClient();
    client.parseArgs(args);

    // Attempt an orderly shutdown, if the JVM is shutdown via a signal.
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        client.shutdown();
      }
    });

    try {
      client.startMetricsService();
      client.runStressTest();
      client.blockUntilStressTestComplete();
    } catch (Exception e) {
      log.log(Level.WARNING, "The stress test client encountered an error!", e);
    } finally {
      client.shutdown();
    }
  }

  private static final int WORKER_GRACE_PERIOD_SECS = 30;

  private List<InetSocketAddress> addresses =
      singletonList(new InetSocketAddress("localhost", 8080));
  private List<TestCaseWeightPair> testCaseWeightPairs = new ArrayList<>();

  private String serverHostOverride;
  private boolean useTls = false;
  private boolean useTestCa = false;
  private int durationSecs = -1;
  private int channelsPerServer = 1;
  private int stubsPerChannel = 1;
  private int metricsPort = 8081;

  private Server metricsServer;
  private final Map<String, Metrics.GaugeResponse> gauges =
      new ConcurrentHashMap<>();

  private volatile boolean shutdown;

  /**
   * List of futures that {@link #blockUntilStressTestComplete()} waits for.
   */
  private final List<ListenableFuture<?>> workerFutures =
      new ArrayList<>();
  private final List<ManagedChannel> channels = new ArrayList<>();
  private ListeningExecutorService threadpool;

  @VisibleForTesting
  void parseArgs(String[] args) {
    boolean usage = false;
    String serverAddresses = "";
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
      if ("server_addresses".equals(key)) {
        // May need to apply server host overrides to the addresses, so delay processing
        serverAddresses = value;
      } else if ("server_host_override".equals(key)) {
        serverHostOverride = value;
      } else if ("use_tls".equals(key)) {
        useTls = Boolean.parseBoolean(value);
      } else if ("use_test_ca".equals(key)) {
        useTestCa = Boolean.parseBoolean(value);
      } else if ("test_cases".equals(key)) {
        testCaseWeightPairs = parseTestCases(value);
      } else if ("test_duration_secs".equals(key)) {
        durationSecs = Integer.valueOf(value);
      } else if ("num_channels_per_server".equals(key)) {
        channelsPerServer = Integer.valueOf(value);
      } else if ("num_stubs_per_channel".equals(key)) {
        stubsPerChannel = Integer.valueOf(value);
      } else if ("metrics_port".equals(key)) {
        metricsPort = Integer.valueOf(value);
      } else {
        System.err.println("Unknown argument: " + key);
        usage = true;
        break;
      }
    }

    if (!usage && !serverAddresses.isEmpty()) {
      addresses = parseServerAddresses(serverAddresses);
      usage = addresses.isEmpty();
    }

