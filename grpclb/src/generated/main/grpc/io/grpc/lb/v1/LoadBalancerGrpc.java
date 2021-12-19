 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler",
    comments = "Source: grpc/lb/v1/load_balancer.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class LoadBalancerGrpc {

  private LoadBalancerGrpc() {}

  public static final String SERVICE_NAME = "grpc.lb.v1.LoadBalancer";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.grpc.lb.v1.LoadBalanceRequest,
      io.grpc.lb.v1.LoadBalanceResponse> getBalanceLoadMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "BalanceLoad",
      requestType = io.grpc.lb.v1.LoadBalanceRequest.class,
      responseType = io.grpc.lb.v1.LoadBalanceResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<io.grpc.lb.v1.LoadBalanceRequest,
      io.grpc.lb.v1.LoadBalanceResponse> getBalanceLoadMethod() {
    io.grpc.MethodDescriptor<io.grpc.lb.v1.LoadBalanceRequest, io.grpc.lb.v1.LoadBalanceResponse> getBalanceLoadMethod;
    if ((getBalanceLoadMethod = LoadBalancerGrpc.getBalanceLoadMethod) == null) {
