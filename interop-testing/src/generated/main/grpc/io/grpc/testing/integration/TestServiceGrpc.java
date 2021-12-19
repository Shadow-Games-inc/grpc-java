@javax.annotation.Generated(
    value = "by gRPC proto compiler",
    comments = "Source: grpc/testing/test.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class TestServiceGrpc {

  private TestServiceGrpc() {}

  public static final String SERVICE_NAME = "grpc.testing.TestService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.grpc.testing.integration.EmptyProtos.Empty,
      io.grpc.testing.integration.EmptyProtos.Empty> getEmptyCallMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "EmptyCall",
      requestType = io.grpc.testing.integration.EmptyProtos.Empty.class,
      responseType = io.grpc.testing.integration.EmptyProtos.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.grpc.testing.integration.EmptyProtos.Empty,
