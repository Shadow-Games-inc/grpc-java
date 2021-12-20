/*
 * Copyright 2019 The gRPC Authors
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

import static com.google.common.base.Charsets.UTF_8;
import static org.junit.Assert.assertEquals;

import com.google.common.io.CharStreams;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.alts.ComputeEngineChannelBuilder;
import io.grpc.testing.integration.Messages.GrpclbRouteType;
import io.grpc.testing.integration.Messages.SimpleRequest;
import io.grpc.testing.integration.Messages.SimpleResponse;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Test client that verifies that grpclb failover into fallback mode works under
 * different failure modes.
 * This client is suitable for testing fallback with any "grpclb" load-balanced
 * service, but is particularly meant to implement a set of test cases described
 * in an internal doc titled "DirectPath Cloud-to-Prod End-to-End Test Cases",
 * section "gRPC DirectPath-to-CFE fallback".
 */
public final class GrpclbFallbackTestClient {
  private static final Logger logger =
      Logger.getLogger(GrpclbFallbackTestClient.class.getName());

  /**
   * Entry point.
   */
  public static void main(String[] args) throws Exception {
    final GrpclbFallbackTestClient client = new GrpclbFallbackTestClient();
    client.parseArgs(args);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      @SuppressWarnings("CatchAndPrintStackTrace")
      public void run() {
        System.out.println("Shutting down");
        try {
          client.tearDown();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
    try {
      client.run();
    } finally {
      client.tearDown();
    }
    System.exit(0);
  }

  private String unrouteLbAndBackendAddrsCmd = "exit 1";
  private String blackholeLbAndBackendAddrsCmd = "exit 1";
  private String serverUri;
  private String customCredentialsType;
  private String testCase;

  private ManagedChannel channel;
  private TestServiceGrpc.TestServiceBlockingStub blockingStub;

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
      if ("server_uri".equals(key)) {
        serverUri = value;
      } else if ("test_case".equals(key)) {
        testCase = value;
      } else if ("unroute_lb_and_backend_addrs_cmd".equals(key)) {
        unrouteLbAndBackendAddrsCmd = value;
      } else if ("blackhole_lb_and_backend_addrs_cmd".equals(key)) {
        blackholeLbAndBackendAddrsCmd = value;
      } else if ("custom_credentials_type".equals(key)) {
        customCredentialsType = value;
      } else {
        System.err.println("Unknown argument: " + key);
        usage = true;
        break;
      }
    }
    if (usage) {
      GrpclbFallbackTestClient c = new GrpclbFallbackTestClient();
      System.out.println(
          "Usage: [ARGS...]"
          + "\n"
          + "\n  --server_uri                          Server target. Default: "
          + c.serverUri
          + "\n  --custom_credentials_type             Name of Credentials to use. "
          + "Default: " + c.customCredentialsType
          + "\n  --unroute_lb_and_backend_addrs_cmd    Shell command used to make "
          + "LB and backend addresses unroutable. Default: "
          + c.unrouteLbAndBackendAddrsCmd
          + "\n  --blackhole_lb_and_backend_addrs_cmd  Shell command used to make "
          + "LB and backend addresses black holed. Default: "
          + c.blackholeLbAndBackendAddrsCmd
          + "\n  --test_case=TEST_CASE        Test case to run. Valid options are:"
          + "\n      fast_fallback_before_startup : fallback before LB connection"
          + "\n      fast_fallback_after_startup : fallback after startup due to "
          + "LB/backend addresses becoming unroutable"
          + "\n      slow_fallback_before_startup : fallback before LB connection "
          + "due to LB/backend addresses being blackholed"
          + "\n      slow_fallback_after_startup : fallback after startup due to "
          + "LB/backend addresses becoming blackholed"
          + "\n      Default: " + c.testCase
      );
      System.exit(1);
    }
  }

  private ManagedChannel createChannel() {
    if (!customCredentialsType.equals("compute_engine_channel_creds")) {
      throw new AssertionError(
          "This test currently only supports "
          + "--custom_credentials_type=compute_engine_channel_creds. "
          + "TODO: add support for other types.");
    }
    ComputeEngineChannelBuilder builder = ComputeEngineChannelBuilder.forTarget(serverUri);
    builder.keepAliveTime(3600, TimeUnit.SECONDS);
    builder.keepAliveTimeout(20, TimeUnit.SECONDS);
    return builder.build();
  }

  void initStub() {
    channel = createChannel();
    blockingStub = TestServiceGrpc.newBlockingStub(channel);
  }

  private void tearDown() {
    try {
      if (channel != null) {
        channel.shutdownNow();
