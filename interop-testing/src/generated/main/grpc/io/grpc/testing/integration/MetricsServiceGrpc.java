@javax.annotation.Generated(
    value = "by gRPC proto compiler",
    comments = "Source: grpc/testing/metrics.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class MetricsServiceGrpc {

  private MetricsServiceGrpc() {}

  public static final String SERVICE_NAME = "grpc.testing.MetricsService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.grpc.testing.integration.Metrics.EmptyMessage,
      io.grpc.testing.integration.Metrics.GaugeResponse> getGetAllGaugesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetAllGauges",
      requestType = io.grpc.testing.integration.Metrics.EmptyMessage.class,
      responseType = io.grpc.testing.integration.Metrics.GaugeResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<io.grpc.testing.integration.Metrics.EmptyMessage,
      io.grpc.testing.integration.Metrics.GaugeResponse> getGetAllGaugesMethod() {
    io.grpc.MethodDescriptor<io.grpc.testing.integration.Metrics.EmptyMessage, io.grpc.testing.integration.Metrics.GaugeResponse> getGetAllGaugesMethod;
