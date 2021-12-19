
import io.grpc.Attributes;
import io.grpc.ConnectivityStateInfo;
import io.grpc.EquivalentAddressGroup;
import io.grpc.LoadBalancer.Subchannel;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Manages life-cycle of Subchannels for {@link GrpclbState}.
 *
 * <p>All methods are run from the ChannelExecutor that the helper uses.
