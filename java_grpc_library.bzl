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
        _JavaRpcToolchainInfo(
            java_toolchain = ctx.attr._java_toolchain,
            plugin = ctx.executable.plugin,
            plugin_arg = ctx.attr.plugin_arg,
            protoc = ctx.executable._protoc,
            runtime = ctx.attr.runtime,
        ),
        platform_common.ToolchainInfo(),  # Magic for b/78647825
    ]
