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

import static com.google.common.truth.Truth.assertThat;
import static io.grpc.xds.XdsLbPolicies.CLUSTER_RESOLVER_POLICY_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Iterables;
import io.grpc.Attributes;
import io.grpc.ConnectivityState;
import io.grpc.EquivalentAddressGroup;
import io.grpc.LoadBalancer;
import io.grpc.LoadBalancer.Helper;
import io.grpc.LoadBalancer.PickResult;
import io.grpc.LoadBalancer.PickSubchannelArgs;
import io.grpc.LoadBalancer.ResolvedAddresses;
import io.grpc.LoadBalancer.Subchannel;
import io.grpc.LoadBalancer.SubchannelPicker;
import io.grpc.LoadBalancerProvider;
import io.grpc.LoadBalancerRegistry;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.SynchronizationContext;
import io.grpc.internal.ObjectPool;
import io.grpc.xds.CdsLoadBalancerProvider.CdsConfig;
import io.grpc.xds.ClusterResolverLoadBalancerProvider.ClusterResolverConfig;
import io.grpc.xds.ClusterResolverLoadBalancerProvider.ClusterResolverConfig.DiscoveryMechanism;
import io.grpc.xds.EnvoyServerProtoData.UpstreamTlsContext;
import io.grpc.xds.RingHashLoadBalancer.RingHashConfig;
import io.grpc.xds.XdsClient.CdsUpdate;
import io.grpc.xds.internal.sds.CommonTlsContextTestsUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link CdsLoadBalancer2}.
 */
@RunWith(JUnit4.class)
public class CdsLoadBalancer2Test {

  private static final String CLUSTER = "cluster-foo.googleapis.com";
  private static final String EDS_SERVICE_NAME = "backend-service-1.googleapis.com";
  private static final String DNS_HOST_NAME = "backend-service-dns.googleapis.com:443";
  private static final String LRS_SERVER_NAME = "lrs.googleapis.com";
  private final UpstreamTlsContext upstreamTlsContext =
      CommonTlsContextTestsUtil.buildUpstreamTlsContext("google_cloud_private_spiffe", true);

  private final SynchronizationContext syncContext = new SynchronizationContext(
      new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
          throw new AssertionError(e);
        }
      });
  private final LoadBalancerRegistry lbRegistry = new LoadBalancerRegistry();
  private final List<FakeLoadBalancer> childBalancers = new ArrayList<>();
  private final FakeXdsClient xdsClient = new FakeXdsClient();
  private final ObjectPool<XdsClient> xdsClientPool = new ObjectPool<XdsClient>() {
    @Override
    public XdsClient getObject() {
      xdsClientRefs++;
      return xdsClient;
    }

    @Override
    public XdsClient returnObject(Object object) {
      xdsClientRefs--;
      return null;
    }
  };

  @Mock
  private Helper helper;
  @Captor
  private ArgumentCaptor<SubchannelPicker> pickerCaptor;
  private int xdsClientRefs;
  private CdsLoadBalancer2  loadBalancer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    when(helper.getSynchronizationContext()).thenReturn(syncContext);
    lbRegistry.register(new FakeLoadBalancerProvider(CLUSTER_RESOLVER_POLICY_NAME));
    lbRegistry.register(new FakeLoadBalancerProvider("round_robin"));
    lbRegistry.register(new FakeLoadBalancerProvider("ring_hash"));
    loadBalancer = new CdsLoadBalancer2(helper, lbRegistry);
    loadBalancer.handleResolvedAddresses(
        ResolvedAddresses.newBuilder()
            .setAddresses(Collections.<EquivalentAddressGroup>emptyList())
            .setAttributes(
                // Other attributes not used by cluster_resolver LB are omitted.
                Attributes.newBuilder()
                    .set(InternalXdsAttributes.XDS_CLIENT_POOL, xdsClientPool)
                    .build())
            .setLoadBalancingPolicyConfig(new CdsConfig(CLUSTER))
            .build());
    assertThat(Iterables.getOnlyElement(xdsClient.watchers.keySet())).isEqualTo(CLUSTER);
  }

  @After
  public void tearDown() {
    loadBalancer.shutdown();
    assertThat(xdsClient.watchers).isEmpty();
    assertThat(xdsClientRefs).isEqualTo(0);
    assertThat(childBalancers).isEmpty();
  }

  @Test
  public void discoverTopLevelEdsCluster() {
    CdsUpdate update =
        CdsUpdate.forEds(CLUSTER, EDS_SERVICE_NAME, LRS_SERVER_NAME, 100L, upstreamTlsContext)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    assertThat(childBalancers).hasSize(1);
    FakeLoadBalancer childBalancer = Iterables.getOnlyElement(childBalancers);
    assertThat(childBalancer.name).isEqualTo(CLUSTER_RESOLVER_POLICY_NAME);
    ClusterResolverConfig childLbConfig = (ClusterResolverConfig) childBalancer.config;
    assertThat(childLbConfig.discoveryMechanisms).hasSize(1);
    DiscoveryMechanism instance = Iterables.getOnlyElement(childLbConfig.discoveryMechanisms);
    assertDiscoveryMechanism(instance, CLUSTER, DiscoveryMechanism.Type.EDS, EDS_SERVICE_NAME,
        null, LRS_SERVER_NAME, 100L, upstreamTlsContext);
    assertThat(childLbConfig.lbPolicy.getProvider().getPolicyName()).isEqualTo("round_robin");
  }

  @Test
  public void discoverTopLevelLogicalDnsCluster() {
    CdsUpdate update =
        CdsUpdate.forLogicalDns(CLUSTER, DNS_HOST_NAME, LRS_SERVER_NAME, 100L, upstreamTlsContext)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    assertThat(childBalancers).hasSize(1);
    FakeLoadBalancer childBalancer = Iterables.getOnlyElement(childBalancers);
    assertThat(childBalancer.name).isEqualTo(CLUSTER_RESOLVER_POLICY_NAME);
    ClusterResolverConfig childLbConfig = (ClusterResolverConfig) childBalancer.config;
    assertThat(childLbConfig.discoveryMechanisms).hasSize(1);
    DiscoveryMechanism instance = Iterables.getOnlyElement(childLbConfig.discoveryMechanisms);
    assertDiscoveryMechanism(instance, CLUSTER, DiscoveryMechanism.Type.LOGICAL_DNS, null,
        DNS_HOST_NAME, LRS_SERVER_NAME, 100L, upstreamTlsContext);
    assertThat(childLbConfig.lbPolicy.getProvider().getPolicyName()).isEqualTo("round_robin");
  }

  @Test
  public void nonAggregateCluster_resourceNotExist_returnErrorPicker() {
    xdsClient.deliverResourceNotExist(CLUSTER);
    verify(helper).updateBalancingState(
        eq(ConnectivityState.TRANSIENT_FAILURE), pickerCaptor.capture());
    Status unavailable = Status.UNAVAILABLE.withDescription(
        "CDS error: found 0 leaf (logical DNS or EDS) clusters for root cluster " + CLUSTER);
    assertPicker(pickerCaptor.getValue(), unavailable, null);
    assertThat(childBalancers).isEmpty();
  }

  @Test
  public void nonAggregateCluster_resourceUpdate() {
    CdsUpdate update =
        CdsUpdate.forEds(CLUSTER, null, null, 100L, upstreamTlsContext)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    assertThat(childBalancers).hasSize(1);
    FakeLoadBalancer childBalancer = Iterables.getOnlyElement(childBalancers);
    ClusterResolverConfig childLbConfig = (ClusterResolverConfig) childBalancer.config;
    DiscoveryMechanism instance = Iterables.getOnlyElement(childLbConfig.discoveryMechanisms);
    assertDiscoveryMechanism(instance, CLUSTER, DiscoveryMechanism.Type.EDS, null, null, null,
        100L, upstreamTlsContext);

    update = CdsUpdate.forEds(CLUSTER, EDS_SERVICE_NAME, LRS_SERVER_NAME, 200L, null)
        .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    childLbConfig = (ClusterResolverConfig) childBalancer.config;
    instance = Iterables.getOnlyElement(childLbConfig.discoveryMechanisms);
    assertDiscoveryMechanism(instance, CLUSTER, DiscoveryMechanism.Type.EDS, EDS_SERVICE_NAME,
        null, LRS_SERVER_NAME, 200L, null);
  }

  @Test
  public void nonAggregateCluster_resourceRevoked() {
    CdsUpdate update =
        CdsUpdate.forLogicalDns(CLUSTER, DNS_HOST_NAME, null, 100L, upstreamTlsContext)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    assertThat(childBalancers).hasSize(1);
    FakeLoadBalancer childBalancer = Iterables.getOnlyElement(childBalancers);
    ClusterResolverConfig childLbConfig = (ClusterResolverConfig) childBalancer.config;
    DiscoveryMechanism instance = Iterables.getOnlyElement(childLbConfig.discoveryMechanisms);
    assertDiscoveryMechanism(instance, CLUSTER, DiscoveryMechanism.Type.LOGICAL_DNS, null,
        DNS_HOST_NAME, null, 100L, upstreamTlsContext);

    xdsClient.deliverResourceNotExist(CLUSTER);
    assertThat(childBalancer.shutdown).isTrue();
    Status unavailable = Status.UNAVAILABLE.withDescription(
        "CDS error: found 0 leaf (logical DNS or EDS) clusters for root cluster " + CLUSTER);
    verify(helper).updateBalancingState(
        eq(ConnectivityState.TRANSIENT_FAILURE), pickerCaptor.capture());
    assertPicker(pickerCaptor.getValue(), unavailable, null);
    assertThat(childBalancer.shutdown).isTrue();
    assertThat(childBalancers).isEmpty();
  }

  @Test
  public void discoverAggregateCluster() {
    String cluster1 = "cluster-01.googleapis.com";
    String cluster2 = "cluster-02.googleapis.com";
    // CLUSTER (aggr.) -> [cluster1 (aggr.), cluster2 (logical DNS)]
    CdsUpdate update =
        CdsUpdate.forAggregate(CLUSTER, Arrays.asList(cluster1, cluster2))
            .ringHashLbPolicy(100L, 1000L).build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    assertThat(xdsClient.watchers.keySet()).containsExactly(CLUSTER, cluster1, cluster2);
    assertThat(childBalancers).isEmpty();
    String cluster3 = "cluster-03.googleapis.com";
    String cluster4 = "cluster-04.googleapis.com";
    // cluster1 (aggr.) -> [cluster3 (EDS), cluster4 (EDS)]
    CdsUpdate update1 =
        CdsUpdate.forAggregate(cluster1, Arrays.asList(cluster3, cluster4))
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(cluster1, update1);
    assertThat(xdsClient.watchers.keySet()).containsExactly(
        CLUSTER, cluster1, cluster2, cluster3, cluster4);
    assertThat(childBalancers).isEmpty();
    CdsUpdate update3 =
        CdsUpdate.forEds(cluster3, EDS_SERVICE_NAME, LRS_SERVER_NAME, 200L, upstreamTlsContext)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(cluster3, update3);
    assertThat(childBalancers).isEmpty();
    CdsUpdate update2 =
        CdsUpdate.forLogicalDns(cluster2, DNS_HOST_NAME, null, 100L, null)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(cluster2, update2);
    assertThat(childBalancers).isEmpty();
    CdsUpdate update4 =
        CdsUpdate.forEds(cluster4, null, LRS_SERVER_NAME, 300L, null)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(cluster4, update4);
    assertThat(childBalancers).hasSize(1);  // all non-aggregate clusters discovered
    FakeLoadBalancer childBalancer = Iterables.getOnlyElement(childBalancers);
    assertThat(childBalancer.name).isEqualTo(CLUSTER_RESOLVER_POLICY_NAME);
    ClusterResolverConfig childLbConfig = (ClusterResolverConfig) childBalancer.config;
    assertThat(childLbConfig.discoveryMechanisms).hasSize(3);
    // Clusters on higher level has higher priority: [cluster2, cluster3, cluster4]
    assertDiscoveryMechanism(childLbConfig.discoveryMechanisms.get(0), cluster2,
        DiscoveryMechanism.Type.LOGICAL_DNS, null, DNS_HOST_NAME, null, 100L, null);
    assertDiscoveryMechanism(childLbConfig.discoveryMechanisms.get(1), cluster3,
        DiscoveryMechanism.Type.EDS, EDS_SERVICE_NAME, null, LRS_SERVER_NAME, 200L,
        upstreamTlsContext);
    assertDiscoveryMechanism(childLbConfig.discoveryMechanisms.get(2), cluster4,
        DiscoveryMechanism.Type.EDS, null, null, LRS_SERVER_NAME, 300L, null);
    assertThat(childLbConfig.lbPolicy.getProvider().getPolicyName())
        .isEqualTo("ring_hash");  // dominated by top-level cluster's config
    assertThat(((RingHashConfig) childLbConfig.lbPolicy.getConfig()).minRingSize).isEqualTo(100L);
    assertThat(((RingHashConfig) childLbConfig.lbPolicy.getConfig()).maxRingSize).isEqualTo(1000L);
  }

  @Test
  public void aggregateCluster_noNonAggregateClusterExits_returnErrorPicker() {
    String cluster1 = "cluster-01.googleapis.com";
    // CLUSTER (aggr.) -> [cluster1 (EDS)]
    CdsUpdate update =
        CdsUpdate.forAggregate(CLUSTER, Collections.singletonList(cluster1))
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    assertThat(xdsClient.watchers.keySet()).containsExactly(CLUSTER, cluster1);
    xdsClient.deliverResourceNotExist(cluster1);
    verify(helper).updateBalancingState(
        eq(ConnectivityState.TRANSIENT_FAILURE), pickerCaptor.capture());
    Status unavailable = Status.UNAVAILABLE.withDescription(
        "CDS error: found 0 leaf (logical DNS or EDS) clusters for root cluster " + CLUSTER);
    assertPicker(pickerCaptor.getValue(), unavailable, null);
    assertThat(childBalancers).isEmpty();
  }

  @Test
  public void aggregateCluster_descendantClustersRevoked() {
    String cluster1 = "cluster-01.googleapis.com";
    String cluster2 = "cluster-02.googleapis.com";
    // CLUSTER (aggr.) -> [cluster1 (EDS), cluster2 (logical DNS)]
    CdsUpdate update =
        CdsUpdate.forAggregate(CLUSTER, Arrays.asList(cluster1, cluster2))
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    assertThat(xdsClient.watchers.keySet()).containsExactly(CLUSTER, cluster1, cluster2);
    CdsUpdate update1 =
        CdsUpdate.forEds(cluster1, EDS_SERVICE_NAME, LRS_SERVER_NAME, 200L, upstreamTlsContext)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(cluster1, update1);
    CdsUpdate update2 =
        CdsUpdate.forLogicalDns(cluster2, DNS_HOST_NAME, LRS_SERVER_NAME, 100L, null)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(cluster2, update2);
    FakeLoadBalancer childBalancer = Iterables.getOnlyElement(childBalancers);
    ClusterResolverConfig childLbConfig = (ClusterResolverConfig) childBalancer.config;
    assertThat(childLbConfig.discoveryMechanisms).hasSize(2);
    assertDiscoveryMechanism(childLbConfig.discoveryMechanisms.get(0), cluster1,
        DiscoveryMechanism.Type.EDS, EDS_SERVICE_NAME, null, LRS_SERVER_NAME, 200L,
        upstreamTlsContext);
    assertDiscoveryMechanism(childLbConfig.discoveryMechanisms.get(1), cluster2,
        DiscoveryMechanism.Type.LOGICAL_DNS, null, DNS_HOST_NAME, LRS_SERVER_NAME, 100L, null);

    // Revoke cluster1, should still be able to proceed with cluster2.
    xdsClient.deliverResourceNotExist(cluster1);
    assertThat(xdsClient.watchers.keySet()).containsExactly(CLUSTER, cluster1, cluster2);
    childLbConfig = (ClusterResolverConfig) childBalancer.config;
    assertThat(childLbConfig.discoveryMechanisms).hasSize(1);
    assertDiscoveryMechanism(Iterables.getOnlyElement(childLbConfig.discoveryMechanisms), cluster2,
        DiscoveryMechanism.Type.LOGICAL_DNS, null, DNS_HOST_NAME, LRS_SERVER_NAME, 100L, null);
    verify(helper, never()).updateBalancingState(
        eq(ConnectivityState.TRANSIENT_FAILURE), any(SubchannelPicker.class));

    // All revoked.
    xdsClient.deliverResourceNotExist(cluster2);
    verify(helper).updateBalancingState(
        eq(ConnectivityState.TRANSIENT_FAILURE), pickerCaptor.capture());
    Status unavailable = Status.UNAVAILABLE.withDescription(
        "CDS error: found 0 leaf (logical DNS or EDS) clusters for root cluster " + CLUSTER);
    assertPicker(pickerCaptor.getValue(), unavailable, null);
    assertThat(childBalancer.shutdown).isTrue();
    assertThat(childBalancers).isEmpty();
  }

  @Test
  public void aggregateCluster_rootClusterRevoked() {
    String cluster1 = "cluster-01.googleapis.com";
    String cluster2 = "cluster-02.googleapis.com";
    // CLUSTER (aggr.) -> [cluster1 (EDS), cluster2 (logical DNS)]
    CdsUpdate update =
        CdsUpdate.forAggregate(CLUSTER, Arrays.asList(cluster1, cluster2))
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    assertThat(xdsClient.watchers.keySet()).containsExactly(CLUSTER, cluster1, cluster2);
    CdsUpdate update1 =
        CdsUpdate.forEds(cluster1, EDS_SERVICE_NAME, LRS_SERVER_NAME, 200L, upstreamTlsContext)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(cluster1, update1);
    CdsUpdate update2 =
        CdsUpdate.forLogicalDns(cluster2, DNS_HOST_NAME, LRS_SERVER_NAME, 100L, null)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(cluster2, update2);
    FakeLoadBalancer childBalancer = Iterables.getOnlyElement(childBalancers);
    ClusterResolverConfig childLbConfig = (ClusterResolverConfig) childBalancer.config;
    assertThat(childLbConfig.discoveryMechanisms).hasSize(2);
    assertDiscoveryMechanism(childLbConfig.discoveryMechanisms.get(0), cluster1,
        DiscoveryMechanism.Type.EDS, EDS_SERVICE_NAME, null, LRS_SERVER_NAME, 200L,
        upstreamTlsContext);
    assertDiscoveryMechanism(childLbConfig.discoveryMechanisms.get(1), cluster2,
        DiscoveryMechanism.Type.LOGICAL_DNS, null, DNS_HOST_NAME, LRS_SERVER_NAME, 100L, null);

    xdsClient.deliverResourceNotExist(CLUSTER);
    assertThat(xdsClient.watchers.keySet())
        .containsExactly(CLUSTER);  // subscription to all descendant clusters cancelled
    verify(helper).updateBalancingState(
        eq(ConnectivityState.TRANSIENT_FAILURE), pickerCaptor.capture());
    Status unavailable = Status.UNAVAILABLE.withDescription(
        "CDS error: found 0 leaf (logical DNS or EDS) clusters for root cluster " + CLUSTER);
    assertPicker(pickerCaptor.getValue(), unavailable, null);
    assertThat(childBalancer.shutdown).isTrue();
    assertThat(childBalancers).isEmpty();
  }

  @Test
  public void aggregateCluster_intermediateClusterChanges() {
    String cluster1 = "cluster-01.googleapis.com";
    // CLUSTER (aggr.) -> [cluster1]
    CdsUpdate update =
        CdsUpdate.forAggregate(CLUSTER, Collections.singletonList(cluster1))
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    assertThat(xdsClient.watchers.keySet()).containsExactly(CLUSTER, cluster1);

    // CLUSTER (aggr.) -> [cluster2 (aggr.)]
    String cluster2 = "cluster-02.googleapis.com";
    update =
        CdsUpdate.forAggregate(CLUSTER, Collections.singletonList(cluster2))
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    assertThat(xdsClient.watchers.keySet()).containsExactly(CLUSTER, cluster2);

    // cluster2 (aggr.) -> [cluster3 (EDS)]
    String cluster3 = "cluster-03.googleapis.com";
    CdsUpdate update2 =
        CdsUpdate.forAggregate(cluster2, Collections.singletonList(cluster3))
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(cluster2, update2);
    assertThat(xdsClient.watchers.keySet()).containsExactly(CLUSTER, cluster2, cluster3);
    CdsUpdate update3 =
        CdsUpdate.forEds(cluster3, EDS_SERVICE_NAME, LRS_SERVER_NAME, 100L, upstreamTlsContext)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(cluster3, update3);
    FakeLoadBalancer childBalancer = Iterables.getOnlyElement(childBalancers);
    ClusterResolverConfig childLbConfig = (ClusterResolverConfig) childBalancer.config;
    assertThat(childLbConfig.discoveryMechanisms).hasSize(1);
    DiscoveryMechanism instance = Iterables.getOnlyElement(childLbConfig.discoveryMechanisms);
    assertDiscoveryMechanism(instance, cluster3, DiscoveryMechanism.Type.EDS, EDS_SERVICE_NAME,
        null, LRS_SERVER_NAME, 100L, upstreamTlsContext);

    // cluster2 revoked
    xdsClient.deliverResourceNotExist(cluster2);
    assertThat(xdsClient.watchers.keySet())
        .containsExactly(CLUSTER, cluster2);  // cancelled subscription to cluster3
    verify(helper).updateBalancingState(
        eq(ConnectivityState.TRANSIENT_FAILURE), pickerCaptor.capture());
    Status unavailable = Status.UNAVAILABLE.withDescription(
        "CDS error: found 0 leaf (logical DNS or EDS) clusters for root cluster " + CLUSTER);
    assertPicker(pickerCaptor.getValue(), unavailable, null);
    assertThat(childBalancer.shutdown).isTrue();
    assertThat(childBalancers).isEmpty();
  }

  @Test
  public void aggregateCluster_discoveryErrorBeforeChildLbCreated_returnErrorPicker() {
    String cluster1 = "cluster-01.googleapis.com";
    // CLUSTER (aggr.) -> [cluster1]
    CdsUpdate update =
        CdsUpdate.forAggregate(CLUSTER, Collections.singletonList(cluster1))
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    assertThat(xdsClient.watchers.keySet()).containsExactly(CLUSTER, cluster1);
    Status error = Status.RESOURCE_EXHAUSTED.withDescription("OOM");
    xdsClient.deliverError(error);
    verify(helper).updateBalancingState(
        eq(ConnectivityState.TRANSIENT_FAILURE), pickerCaptor.capture());
    assertPicker(pickerCaptor.getValue(), error, null);
    assertThat(childBalancers).isEmpty();
  }

  @Test
  public void aggregateCluster_discoveryErrorAfterChildLbCreated_propagateToChildLb() {
    String cluster1 = "cluster-01.googleapis.com";
    // CLUSTER (aggr.) -> [cluster1 (logical DNS)]
    CdsUpdate update =
        CdsUpdate.forAggregate(CLUSTER, Collections.singletonList(cluster1))
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    CdsUpdate update1 =
        CdsUpdate.forLogicalDns(cluster1, DNS_HOST_NAME, LRS_SERVER_NAME, 200L, null)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(cluster1, update1);
    FakeLoadBalancer childLb = Iterables.getOnlyElement(childBalancers);
    ClusterResolverConfig childLbConfig = (ClusterResolverConfig) childLb.config;
    assertThat(childLbConfig.discoveryMechanisms).hasSize(1);

    Status error = Status.RESOURCE_EXHAUSTED.withDescription("OOM");
    xdsClient.deliverError(error);
    assertThat(childLb.upstreamError).isEqualTo(error);
    assertThat(childLb.shutdown).isFalse();  // child LB may choose to keep working
  }

  @Test
  public void handleNameResolutionErrorFromUpstream_beforeChildLbCreated_returnErrorPicker() {
    Status upstreamError = Status.UNAVAILABLE.withDescription("unreachable");
    loadBalancer.handleNameResolutionError(upstreamError);
    verify(helper).updateBalancingState(
        eq(ConnectivityState.TRANSIENT_FAILURE), pickerCaptor.capture());
    assertPicker(pickerCaptor.getValue(), upstreamError, null);
  }

  @Test
  public void handleNameResolutionErrorFromUpstream_afterChildLbCreated_fallThrough() {
    CdsUpdate update =
        CdsUpdate.forEds(CLUSTER, EDS_SERVICE_NAME, LRS_SERVER_NAME, 100L, upstreamTlsContext)
            .roundRobinLbPolicy().build();
    xdsClient.deliverCdsUpdate(CLUSTER, update);
    FakeLoadBalancer childBalancer = Iterables.getOnlyElement(childBalancers);
    assertThat(childBalancer.shutdown).isFalse();
    loadBalancer.handleNameResolutionError(Status.UNAVAILABLE.withDescription("unreachable"));
    assertThat(childBalancer.upstreamError.getCode()).isEqualTo(Code.UNAVAILABLE);
    assertThat(childBalancer.upstreamError.getDescription()).isEqualTo("unreachable");
    verify(helper, never()).updateBalancingState(
        any(ConnectivityState.class), any(SubchannelPicker.class));
  }

  private static void assertPicker(SubchannelPicker picker, Status expectedStatus,
      @Nullable Subchannel expectedSubchannel)  {
    PickResult result = picker.pickSubchannel(mock(PickSubchannelArgs.class));
    Status actualStatus = result.getStatus();
    assertThat(actualStatus.getCode()).isEqualTo(expectedStatus.getCode());
    assertThat(actualStatus.getDescription()).isEqualTo(expectedStatus.getDescription());
    if (actualStatus.isOk()) {
      assertThat(result.getSubchannel()).isSameInstanceAs(expectedSubchannel);
    }
  }

  private static void assertDiscoveryMechanism(DiscoveryMechanism instance, String name,
      DiscoveryMechanism.Type type, @Nullable String edsServiceName, @Nullable String dnsHostName,
      @Nullable String lrsServerName, @Nullable Long maxConcurrentRequests,
      @Nullable UpstreamTlsContext tlsContext) {
    assertThat(instance.cluster).isEqualTo(name);
    assertThat(instance.type).isEqualTo(type);
    assertThat(instance.edsServiceName).isEqualTo(edsServiceName);
    assertThat(instance.dnsHostName).isEqualTo(dnsHostName);
    assertThat(instance.lrsServerName).isEqualTo(lrsServerName);
    assertThat(instance.maxConcurrentRequests).isEqualTo(maxConcurrentRequests);
    assertThat(instance.tlsContext).isEqualTo(tlsContext);
  }

  private final class FakeLoadBalancerProvider extends LoadBalancerProvider {
    private final String policyName;

    FakeLoadBalancerProvider(String policyName) {
      this.policyName = policyName;
    }

    @Override
    public LoadBalancer newLoadBalancer(Helper helper) {
      FakeLoadBalancer balancer = new FakeLoadBalancer(policyName, helper);
      childBalancers.add(balancer);
      return balancer;
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public int getPriority() {
      return 0;  // doesn't matter
    }

    @Override
    public String getPolicyName() {
      return policyName;
    }
  }

  private final class FakeLoadBalancer extends LoadBalancer {
    private final String name;
    private final Helper helper;
    private Object config;
    private Status upstreamError;
    private boolean shutdown;

    FakeLoadBalancer(String name, Helper helper) {
      this.name = name;
      this.helper = helper;
    }

    @Override
    public void handleResolvedAddresses(ResolvedAddresses resolvedAddresses) {
      config = resolvedAddresses.getLoadBalancingPolicyConfig();
    }

    @Override
    public void handleNameResolutionError(Status error) {
      upstreamError = error;
    }

    @Override
    public void shutdown() {
      shutdown = true;
      childBalancers.remove(this);
    }

    void deliverSubchannelState(final Subchannel subchannel, ConnectivityState state) {
      SubchannelPicker picker = new SubchannelPicker() {
        @Override
        public PickResult pickSubchannel(PickSubchannelArgs args) {
          return PickResult.withSubchannel(subchannel);
        }
      };
      helper.updateBalancingState(state, picker);
    }
  }

  private static final class FakeXdsClient extends XdsClient {
    private final Map<String, CdsResourceWatcher> watchers = new HashMap<>();

    @Override
    void watchCdsResource(String resourceName, CdsResourceWatcher watcher) {
      assertThat(watchers).doesNotContainKey(resourceName);
      watchers.put(resourceName, watcher);
    }

    @Override
    void cancelCdsResourceWatch(String resourceName, CdsResourceWatcher watcher) {
      assertThat(watchers).containsKey(resourceName);
      watchers.remove(resourceName);
    }

    private void deliverCdsUpdate(String clusterName, CdsUpdate update) {
      if (watchers.containsKey(clusterName)) {
        watchers.get(clusterName).onChanged(update);
      }
    }

    private void deliverResourceNotExist(String clusterName)  {
      if (watchers.containsKey(clusterName)) {
        watchers.get(clusterName).onResourceDoesNotExist(clusterName);
      }
    }

    private void deliverError(Status error) {
      for (CdsResourceWatcher watcher : watchers.values()) {
        watcher.onError(error);
      }
    }
  }
}
