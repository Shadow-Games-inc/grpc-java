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

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.testing.integration.Metrics.EmptyMessage;
import io.grpc.testing.integration.Metrics.GaugeResponse;
import io.grpc.testing.integration.StressTestClient.TestCaseWeightPair;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link StressTestClient}. */
@RunWith(JUnit4.class)
public class StressTestClientTest {

  @Rule
  public final Timeout globalTimeout = Timeout.seconds(10);

  @Test
  public void ipv6AddressesShouldBeSupported() {
    StressTestClient client = new StressTestClient();
    client.parseArgs(new String[] {"--server_addresses=[0:0:0:0:0:0:0:1]:8080,"
        + "[1:2:3:4:f:e:a:b]:8083"});

    assertEquals(2, client.addresses().size());
    assertEquals(new InetSocketAddress("0:0:0:0:0:0:0:1", 8080), client.addresses().get(0));
    assertEquals(new InetSocketAddress("1:2:3:4:f:e:a:b", 8083), client.addresses().get(1));
  }

  @Test
  public void defaults() {
    StressTestClient client = new StressTestClient();
    assertEquals(singletonList(new InetSocketAddress("localhost", 8080)), client.addresses());
    client.parseArgs(new String[] {
        "--server_addresses=localhost:8080,localhost:8081,localhost:8082",
        "--test_cases=empty_unary:20,large_unary:50,server_streaming:30",
