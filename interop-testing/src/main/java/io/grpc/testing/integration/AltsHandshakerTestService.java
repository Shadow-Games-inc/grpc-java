/*
 * Copyright 2021 The gRPC Authors
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

import static com.google.common.base.Preconditions.checkState;
import static io.grpc.alts.internal.HandshakerReq.ReqOneofCase.CLIENT_START;
import static io.grpc.alts.internal.HandshakerReq.ReqOneofCase.NEXT;
import static io.grpc.alts.internal.HandshakerReq.ReqOneofCase.SERVER_START;

import com.google.protobuf.ByteString;
import io.grpc.alts.internal.HandshakerReq;
import io.grpc.alts.internal.HandshakerResp;
import io.grpc.alts.internal.HandshakerResult;
import io.grpc.alts.internal.HandshakerServiceGrpc.HandshakerServiceImplBase;
import io.grpc.alts.internal.Identity;
import io.grpc.alts.internal.RpcProtocolVersions;
import io.grpc.alts.internal.RpcProtocolVersions.Version;
import io.grpc.stub.StreamObserver;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A fake HandshakeService for ALTS integration testing in non-gcp environments.
 * */
public class AltsHandshakerTestService extends HandshakerServiceImplBase {
  private static final Logger log = Logger.getLogger(AltsHandshakerTestService.class.getName());

  private final Random random = new Random();
  private static final int FIXED_LENGTH_OUTPUT = 16;
  private final ByteString fakeOutput = data(FIXED_LENGTH_OUTPUT);
  private final ByteString secret = data(128);
  private State expectState = State.CLIENT_INIT;

  @Override
  public StreamObserver<HandshakerReq> doHandshake(
      final StreamObserver<HandshakerResp> responseObserver) {
    return new StreamObserver<HandshakerReq>() {
      @Override
      public void onNext(HandshakerReq value) {
        log.log(Level.FINE, "request received: " + value);
        synchronized (AltsHandshakerTestService.this) {
          switch (expectState) {
            case CLIENT_INIT:
