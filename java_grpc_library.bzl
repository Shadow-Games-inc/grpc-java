"""Build rule for java_grpc_library."""

_JavaRpcToolchainInfo = provider(
    fields = [
        "java_toolchain",
        "plugin",
        "plugin_arg",
        "protoc",
        "runtime",
    ],
)

def _java_rpc_toolchain_impl(ctx):
    return [
