public final class InternalNettyServerBuilder {
  public static NettyServer buildTransportServers(NettyServerBuilder builder,
      List<? extends ServerStreamTracer.Factory> streamTracerFactories) {
    return builder.buildTransportServers(streamTracerFactories);
  }

  public static void setTransportTracerFactory(NettyServerBuilder builder,
      TransportTracer.Factory transportTracerFactory) {
    builder.setTransportTracerFactory(transportTracerFactory);
  }

  public static void setStatsEnabled(NettyServerBuilder builder, boolean value) {
    builder.setStatsEnabled(value);
  }

  public static void setStatsRecordStartedRpcs(NettyServerBuilder builder, boolean value) {
    builder.setStatsRecordStartedRpcs(value);
  }

  public static void setStatsRecordRealTimeMetrics(NettyServerBuilder builder, boolean value) {
    builder.setStatsRecordRealTimeMetrics(value);
  }

  public static void setTracingEnabled(NettyServerBuilder builder, boolean value) {
    builder.setTracingEnabled(value);
  }
  public static void useNioTransport(NettyServerBuilder builder) {
    builder.channelType(NioServerSocketChannel.class);
    builder
        .bossEventLoopGroupPool(SharedResourcePool.forResource(Utils.NIO_BOSS_EVENT_LOOP_GROUP));
    builder
        .workerEventLoopGroupPool(
            SharedResourcePool.forResource(Utils.NIO_WORKER_EVENT_LOOP_GROUP));
  }
