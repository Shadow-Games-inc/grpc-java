"""Build rule for java_grpc_library."""

_JavaRpcToolchainInfo = provider(
    fields = [
        "java_toolchain",
