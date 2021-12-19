    comments = "Source: grpc/testing/test.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class LoadBalancerStatsServiceGrpc {

  private LoadBalancerStatsServiceGrpc() {}

  public static final String SERVICE_NAME = "grpc.testing.LoadBalancerStatsService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.grpc.testing.integration.Messages.LoadBalancerStatsRequest,
      io.grpc.testing.integration.Messages.LoadBalancerStatsResponse> getGetClientStatsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetClientStats",
      requestType = io.grpc.testing.integration.Messages.LoadBalancerStatsRequest.class,
      responseType = io.grpc.testing.integration.Messages.LoadBalancerStatsResponse.class,
