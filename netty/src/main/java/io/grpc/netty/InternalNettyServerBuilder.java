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
