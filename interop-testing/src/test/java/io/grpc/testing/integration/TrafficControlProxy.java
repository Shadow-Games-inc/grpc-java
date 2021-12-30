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

import static com.google.common.base.Preconditions.checkArgument;

import io.netty.util.concurrent.DefaultThreadFactory;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class TrafficControlProxy {

  private static final int DEFAULT_BAND_BPS = 1024 * 1024;
  private static final int DEFAULT_DELAY_NANOS = 200 * 1000 * 1000;
  private static final Logger logger = Logger.getLogger(TrafficControlProxy.class.getName());

  // TODO: make host and ports arguments
  private final String localhost = "localhost";
  private final int serverPort;
  private final int queueLength;
  private final int chunkSize;
  private final int bandwidth;
  private final long latency;
  private volatile boolean shutDown;
  private ServerSocket clientAcceptor;
  private Socket serverSock;
  private Socket clientSock;
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(5, 10, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
          new DefaultThreadFactory("proxy-pool", true));

  /**
   * Returns a new TrafficControlProxy with default bandwidth and latency.
   */
  public TrafficControlProxy(int serverPort) {
    this(serverPort, DEFAULT_BAND_BPS, DEFAULT_DELAY_NANOS, TimeUnit.NANOSECONDS);
  }

  /**
   * Returns a new TrafficControlProxy with bandwidth set to targetBPS, and latency set to
   * targetLatency in latencyUnits.
   */
  public TrafficControlProxy(int serverPort, int targetBps, int targetLatency,
      TimeUnit latencyUnits) {
    checkArgument(targetBps > 0);
    checkArgument(targetLatency > 0);
    this.serverPort = serverPort;
    bandwidth = targetBps;
    // divide by 2 because latency is applied in both directions
    latency = latencyUnits.toNanos(targetLatency) / 2;
    queueLength = (int) Math.max(bandwidth * latency / TimeUnit.SECONDS.toNanos(1), 1);
    chunkSize = Math.max(1, queueLength);
  }

  /**
   * Starts a new thread that waits for client and server and start reader/writer threads.
   */
  public void start() throws IOException {
    // ClientAcceptor uses a ServerSocket server so that the client can connect to the proxy as it
    // normally would a server. serverSock then connects the server using a regular Socket as a
    // client normally would.
    clientAcceptor = new ServerSocket();
    clientAcceptor.bind(new InetSocketAddress(localhost, 0));
    executor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          clientSock = clientAcceptor.accept();
          serverSock = new Socket();
          serverSock.connect(new InetSocketAddress(localhost, serverPort));
          startWorkers();
