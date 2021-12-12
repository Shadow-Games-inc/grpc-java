package io.grpc.internal;

import io.grpc.LoadBalancer;
import io.grpc.LoadBalancerProvider;
import io.grpc.NameResolver.ConfigOrError;
import java.util.Map;

/**
 * Provider for the "pick_first" balancing policy.
 *
 * <p>This provides no load-balancing over the addresses from the {@link NameResolver}.  It walks
 * down the address list and sticks to the first that works.
 */
public final class PickFirstLoadBalancerProvider extends LoadBalancerProvider {
  private static final String NO_CONFIG = "no service config";

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public int getPriority() {
    return 5;
  }

  @Override
  public String getPolicyName() {
    return "pick_first";
  }

  @Override
  public LoadBalancer newLoadBalancer(LoadBalancer.Helper helper) {
    return new PickFirstLoadBalancer(helper);
  }

  @Override
  public ConfigOrError parseLoadBalancingPolicyConfig(
      Map<String, ?> rawLoadBalancingPolicyConfig) {
    return ConfigOrError.fromConfig(NO_CONFIG);
  }
}
