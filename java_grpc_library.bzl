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

java_rpc_toolchain = rule(
    attrs = {
        # This attribute has a "magic" name recognized by the native DexArchiveAspect (b/78647825).
        "runtime": attr.label_list(
            cfg = "target",
            providers = [JavaInfo],
        ),
        "plugin": attr.label(
            cfg = "host",
            executable = True,
        ),
        "plugin_arg": attr.string(),
        "_protoc": attr.label(
            cfg = "host",
            default = Label("@com_google_protobuf//:protoc"),
            executable = True,
        ),
        "_java_toolchain": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_toolchain"),
        ),
    },
    provides = [
        _JavaRpcToolchainInfo,
        platform_common.ToolchainInfo,
    ],
    implementation = _java_rpc_toolchain_impl,
)

# "repository" here is for Bazel builds that span multiple WORKSPACES.
def _path_ignoring_repository(f):
    # Bazel creates a _virtual_imports directory in case the .proto source files
    # need to be accessed at a path that's different from their source path:
    # https://github.com/bazelbuild/bazel/blob/0.27.1/src/main/java/com/google/devtools/build/lib/rules/proto/ProtoCommon.java#L289
    #
    # In that case, the import path of the .proto file is the path relative to
