 * Copyright 2018 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.internal;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import io.grpc.Status.Code;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Retry policy data object.
 */
@Immutable
final class RetryPolicy {
  final int maxAttempts;
  final long initialBackoffNanos;
  final long maxBackoffNanos;
  final double backoffMultiplier;
  @Nullable
  final Long perAttemptRecvTimeoutNanos;
  final Set<Code> retryableStatusCodes;

  /**
   * The caller is supposed to have validated the arguments and handled throwing exception or
   * logging warnings already, so we avoid repeating args check here.
   */
  RetryPolicy(
      int maxAttempts,
      long initialBackoffNanos,
      long maxBackoffNanos,
      double backoffMultiplier,
      @Nullable Long perAttemptRecvTimeoutNanos,
      @Nonnull Set<Code> retryableStatusCodes) {
    this.maxAttempts = maxAttempts;
    this.initialBackoffNanos = initialBackoffNanos;
    this.maxBackoffNanos = maxBackoffNanos;
    this.backoffMultiplier = backoffMultiplier;
    this.perAttemptRecvTimeoutNanos = perAttemptRecvTimeoutNanos;
    this.retryableStatusCodes = ImmutableSet.copyOf(retryableStatusCodes);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        maxAttempts,
        initialBackoffNanos,
        maxBackoffNanos,
        backoffMultiplier,
        perAttemptRecvTimeoutNanos,
        retryableStatusCodes);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof RetryPolicy)) {
      return false;
    }
    RetryPolicy that = (RetryPolicy) other;
    return this.maxAttempts == that.maxAttempts
        && this.initialBackoffNanos == that.initialBackoffNanos
        && this.maxBackoffNanos == that.maxBackoffNanos
        && Double.compare(this.backoffMultiplier, that.backoffMultiplier) == 0
        && Objects.equal(this.perAttemptRecvTimeoutNanos, that.perAttemptRecvTimeoutNanos)
        && Objects.equal(this.retryableStatusCodes, that.retryableStatusCodes);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("maxAttempts", maxAttempts)
        .add("initialBackoffNanos", initialBackoffNanos)
        .add("maxBackoffNanos", maxBackoffNanos)
        .add("backoffMultiplier", backoffMultiplier)
        .add("perAttemptRecvTimeoutNanos", perAttemptRecvTimeoutNanos)
        .add("retryableStatusCodes", retryableStatusCodes)
        .toString();
  }
}
