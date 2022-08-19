/*
 * Copyright 2020 The gRPC Authors
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

package io.grpc.xds;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.grpc.xds.XdsSubchannelPickers.BUFFER_PICKER;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import io.grpc.Attributes;
import io.grpc.ClientStreamTracer;
import io.grpc.ClientStreamTracer.StreamInfo;
import io.grpc.ConnectivityState;
import io.grpc.EquivalentAddressGroup;
import io.grpc.InternalLogId;
import io.grpc.LoadBalancer;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.internal.ForwardingClientStreamTracer;
import io.grpc.internal.ObjectPool;
import io.grpc.util.ForwardingLoadBalancerHelper;
import io.grpc.util.ForwardingSubchannel;
import io.grpc.xds.ClusterImplLoadBalancerProvider.ClusterImplConfig;
import io.grpc.xds.Endpoints.DropOverload;
import io.grpc.xds.EnvoyServerProtoData.UpstreamTlsContext;
import io.grpc.xds.LoadStatsManager2.ClusterDropStats;
import io.grpc.xds.LoadStatsManager2.ClusterLocalityStats;
import io.grpc.xds.ThreadSafeRandom.ThreadSafeRandomImpl;
import io.grpc.xds.XdsLogger.XdsLogLevel;
import io.grpc.xds.XdsNameResolverProvider.CallCounterProvider;
import io.grpc.xds.XdsSubchannelPickers.ErrorPicker;
import io.grpc.xds.internal.sds.SslContextProviderSupplier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

/**
 * Load balancer for cluster_impl_experimental LB policy. This LB policy is the child LB policy of
 * the priority_experimental LB policy and the parent LB policy of the weighted_target_experimental
 * LB policy in the xDS load balancing hierarchy. This LB policy applies cluster-level
 * configurations to requests sent to the corresponding cluster, such as drop policies, circuit
 * breakers.
 */
final class ClusterImplLoadBalancer extends LoadBalancer {

  @VisibleForTesting
  static final long DEFAULT_PER_CLUSTER_MAX_CONCURRENT_REQUESTS = 1024L;
  @VisibleForTesting
  static boolean enableCircuitBreaking =
      Strings.isNullOrEmpty(System.getenv("GRPC_XDS_EXPERIMENTAL_CIRCUIT_BREAKING"))
          || Boolean.parseBoolean(System.getenv("GRPC_XDS_EXPERIMENTAL_CIRCUIT_BREAKING"));
  @VisibleForTesting
  static boolean enableSecurity =
      Strings.isNullOrEmpty(System.getenv("GRPC_XDS_EXPERIMENTAL_SECURITY_SUPPORT"))
          || Boolean.parseBoolean(System.getenv("GRPC_XDS_EXPERIMENTAL_SECURITY_SUPPORT"));
  private static final Attributes.Key<ClusterLocalityStats> ATTR_CLUSTER_LOCALITY_STATS =
      Attributes.Key.create("io.grpc.xds.ClusterImplLoadBalancer.clusterLocalityStats");

  private final XdsLogger logger;
  private final Helper helper;
  private final ThreadSafeRandom random;
  // The following fields are effectively final.
  private String cluster;
  @Nullable
  private String edsServiceName;
  private ObjectPool<XdsClient> xdsClientPool;
  private XdsClient xdsClient;
  private CallCounterProvider callCounterProvider;
  private ClusterDropStats dropStats;
  private ClusterImplLbHelper childLbHelper;
  private LoadBalancer childLb;

  ClusterImplLoadBalancer(Helper helper) {
    this(helper, ThreadSafeRandomImpl.instance);
  }

  ClusterImplLoadBalancer(Helper helper, ThreadSafeRandom random) {
    this.helper = checkNotNull(helper, "helper");
    this.random = checkNotNull(random, "random");
    InternalLogId logId = InternalLogId.allocate("cluster-impl-lb", helper.getAuthority());
    logger = XdsLogger.withLogId(logId);
    logger.log(XdsLogLevel.INFO, "Created");
  }

  @Override
  public void handleResolvedAddresses(ResolvedAddresses resolvedAddresses) {
    logger.log(XdsLogLevel.DEBUG, "Received resolution result: {0}", resolvedAddresses);
    Attributes attributes = resolvedAddresses.getAttributes();
    if (xdsClientPool == null) {
      xdsClientPool = attributes.get(InternalXdsAttributes.XDS_CLIENT_POOL);
      xdsClient = xdsClientPool.getObject();
    }
    if (callCounterProvider == null) {
      callCounterProvider = attributes.get(InternalXdsAttributes.CALL_COUNTER_PROVIDER);
    }
    ClusterImplConfig config =
        (ClusterImplConfig) resolvedAddresses.getLoadBalancingPolicyConfig();
    if (cluster == null) {
      cluster = config.cluster;
      edsServiceName = config.edsServiceName;
      childLbHelper = new ClusterImplLbHelper(
          callCounterProvider.getOrCreate(config.cluster, config.edsServiceName));
      childLb = config.childPolicy.getProvider().newLoadBalancer(childLbHelper);
      // Assume load report server does not change throughout cluster lifetime.
      if (config.lrsServerName != null) {
        if (config.lrsServerName.isEmpty()) {
          dropStats = xdsClient.addClusterDropStats(cluster, edsServiceName);
        } else {
          logger.log(XdsLogLevel.WARNING, "Cluster {0} config error: can only report load "
              + "to the same management server. Config lrsServerName {1} should be empty. ",
              cluster, config.lrsServerName);
        }
      }
    }
    childLbHelper.updateDropPolicies(config.dropCategories);
    childLbHelper.updateMaxConcurrentRequests(config.maxConcurrentRequests);
    childLbHelper.updateSslContextProviderSupplier(config.tlsContext);
    childLb.handleResolvedAddresses(
        resolvedAddresses.toBuilder()
            .setAttributes(attributes)
            .setLoadBalancingPolicyConfig(config.childPolicy.getConfig())
            .build());
  }

  @Override
  public void handleNameResolutionError(Status error) {
    if (childLb != null) {
      childLb.handleNameResolutionError(error);
    } else {
      helper.updateBalancingState(ConnectivityState.TRANSIENT_FAILURE, new ErrorPicker(error));
    }
  }

  @Override
  public void shutdown() {
    if (dropStats != null) {
      dropStats.release();
    }
    if (childLb != null) {
      childLb.shutdown();
      if (childLbHelper != null) {
        childLbHelper.updateSslContextProviderSupplier(null);
        childLbHelper = null;
      }
    }
    if (xdsClient != null) {
      xdsClient = xdsClientPool.returnObject(xdsClient);
    }
  }

  @Override
  public boolean canHandleEmptyAddressListFromNameResolution() {
    return true;
  }

  /**
   * A decorated {@link LoadBalancer.Helper} that applies configurations for connections
   * or requests to endpoints in the cluster.
   */
  private final class ClusterImplLbHelper extends ForwardingLoadBalancerHelper {
    private final AtomicLong inFlights;
    private ConnectivityState currentState = ConnectivityState.IDLE;
    private SubchannelPicker currentPicker = BUFFER_PICKER;
    private List<DropOverload> dropPolicies = Collections.emptyList();
    private long maxConcurrentRequests = DEFAULT_PER_CLUSTER_MAX_CONCURRENT_REQUESTS;
    @Nullable
    private SslContextProviderSupplier sslContextProviderSupplier;

    private ClusterImplLbHelper(AtomicLong inFlights) {
      this.inFlights = checkNotNull(inFlights, "inFlights");
    }

    @Override
    public void updateBalancingState(ConnectivityState newState, SubchannelPicker newPicker) {
      currentState = newState;
      currentPicker =  newPicker;
      SubchannelPicker picker =
          new RequestLimitingSubchannelPicker(newPicker, dropPolicies, maxConcurrentRequests);
      delegate().updateBalancingState(newState, picker);
    }

    @Override
    public Subchannel createSubchannel(CreateSubchannelArgs args) {
      List<EquivalentAddressGroup> addresses = new ArrayList<>();
      for (EquivalentAddressGroup eag : args.getAddresses()) {
        Attributes.Builder attrBuilder = eag.getAttributes().toBuilder().set(
            InternalXdsAttributes.ATTR_CLUSTER_NAME, cluster);
        if (enableSecurity && sslContextProviderSupplier != null) {
          attrBuilder.set(
              InternalXdsAttributes.ATTR_SSL_CONTEXT_PROVIDER_SUPPLIER,
              sslContextProviderSupplier);
        }
        addresses.add(new EquivalentAddressGroup(eag.getAddresses(), attrBuilder.build()));
      }
      Locality locality = args.getAddresses().get(0).getAttributes().get(
          InternalXdsAttributes.ATTR_LOCALITY);  // all addresses should be in the same locality
      // Endpoint addresses resolved by ClusterResolverLoadBalancer should always contain
      // attributes with its locality, including endpoints in LOGICAL_DNS clusters.
      // In case of not (which really shouldn't), loads are aggregated under an empty locality.
      if (locality == null) {
        locality = Locality.create("", "", "");
      }
      final ClusterLocalityStats localityStats = xdsClient.addClusterLocalityStats(
          cluster, edsServiceName, locality);
      Attributes attrs = args.getAttributes().toBuilder().set(
          ATTR_CLUSTER_LOCALITY_STATS, localityStats).build();
      args = args.toBuilder().setAddresses(addresses).setAttributes(attrs).build();
      final Subchannel subchannel = delegate().createSubchannel(args);

      return new ForwardingSubchannel() {
        @Override
        public void shutdown() {
          localityStats.release();
          delegate().shutdown();
        }

        @Override
        protected Subchannel delegate() {
          return subchannel;
        }
      };
    }

    @Override
    protected Helper delegate()  {
      return helper;
    }

    private void updateDropPolicies(List<DropOverload> dropOverloads) {
      if (!dropPolicies.equals(dropOverloads)) {
        dropPolicies = dropOverloads;
        updateBalancingState(currentState, currentPicker);
      }
    }

    private void updateMaxConcurrentRequests(@Nullable Long maxConcurrentRequests) {
      if (Objects.equals(this.maxConcurrentRequests, maxConcurrentRequests)) {
        return;
      }
      this.maxConcurrentRequests =
          maxConcurrentRequests != null
              ? maxConcurrentRequests
              : DEFAULT_PER_CLUSTER_MAX_CONCURRENT_REQUESTS;
      updateBalancingState(currentState, currentPicker);
    }

    private void updateSslContextProviderSupplier(@Nullable UpstreamTlsContext tlsContext) {
      UpstreamTlsContext currentTlsContext =
          sslContextProviderSupplier != null
              ? (UpstreamTlsContext)sslContextProviderSupplier.getTlsContext()
              : null;
      if (Objects.equals(currentTlsContext,  tlsContext)) {
        return;
      }
      if (sslContextProviderSupplier != null) {
        sslContextProviderSupplier.close();
      }
      sslContextProviderSupplier =
          tlsContext != null
              ? new SslContextProviderSupplier(tlsContext, xdsClient.getTlsContextManager())
              : null;
    }

    private class RequestLimitingSubchannelPicker extends SubchannelPicker {
      private final SubchannelPicker delegate;
      private final List<DropOverload> dropPolicies;
      private final long maxConcurrentRequests;

      private RequestLimitingSubchannelPicker(SubchannelPicker delegate,
          List<DropOverload> dropPolicies, long maxConcurrentRequests) {
        this.delegate = delegate;
        this.dropPolicies = dropPolicies;
        this.maxConcurrentRequests = maxConcurrentRequests;
      }

      @Override
      public PickResult pickSubchannel(PickSubchannelArgs args) {
        for (DropOverload dropOverload : dropPolicies) {
          int rand = random.nextInt(1_000_000);
          if (rand < dropOverload.dropsPerMillion()) {
            logger.log(XdsLogLevel.INFO, "Drop request with category: {0}",
                dropOverload.category());
            if (dropStats != null) {
              dropStats.recordDroppedRequest(dropOverload.category());
            }
            return PickResult.withDrop(
                Status.UNAVAILABLE.withDescription("Dropped: " + dropOverload.category()));
          }
        }
        final PickResult result = delegate.pickSubchannel(args);
        if (result.getStatus().isOk() && result.getSubchannel() != null) {
          if (enableCircuitBreaking) {
            if (inFlights.get() >= maxConcurrentRequests) {
              if (dropStats != null) {
                dropStats.recordDroppedRequest();
              }
              return PickResult.withDrop(Status.UNAVAILABLE.withDescription(
                  "Cluster max concurrent requests limit exceeded"));
            }
          }
          final ClusterLocalityStats stats =
              result.getSubchannel().getAttributes().get(ATTR_CLUSTER_LOCALITY_STATS);
          ClientStreamTracer.Factory tracerFactory = new CountingStreamTracerFactory(
              stats, inFlights, result.getStreamTracerFactory());
          return PickResult.withSubchannel(result.getSubchannel(), tracerFactory);
        }
        return result;
      }

      @Override
      public String toString() {
        return MoreObjects.toStringHelper(this).add("delegate", delegate).toString();
      }
    }
  }

  private static final class CountingStreamTracerFactory extends
      ClientStreamTracer.InternalLimitedInfoFactory {
    private ClusterLocalityStats stats;
    private final AtomicLong inFlights;
    @Nullable
    private final ClientStreamTracer.Factory delegate;

    private CountingStreamTracerFactory(
        ClusterLocalityStats stats, AtomicLong inFlights,
        @Nullable ClientStreamTracer.Factory delegate) {
      this.stats = checkNotNull(stats, "stats");
      this.inFlights = checkNotNull(inFlights, "inFlights");
      this.delegate = delegate;
    }

    @Override
    public ClientStreamTracer newClientStreamTracer(StreamInfo info, Metadata headers) {
      stats.recordCallStarted();
      inFlights.incrementAndGet();
      if (delegate == null) {
        return new ClientStreamTracer() {
          @Override
          public void streamClosed(Status status) {
            stats.recordCallFinished(status);
            inFlights.decrementAndGet();
          }
        };
      }
      final ClientStreamTracer delegatedTracer = delegate.newClientStreamTracer(info, headers);
      return new ForwardingClientStreamTracer() {
        @Override
        protected ClientStreamTracer delegate() {
          return delegatedTracer;
        }

        @Override
        public void streamClosed(Status status) {
          stats.recordCallFinished(status);
          inFlights.decrementAndGet();
          delegate().streamClosed(status);
        }
      };
    }
  }
}
