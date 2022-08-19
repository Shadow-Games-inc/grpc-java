/*
 * Copyright 2020 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.xds.internal.certprovider;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import io.envoyproxy.envoy.config.core.v3.Node;
import io.envoyproxy.envoy.extensions.transport_sockets.tls.v3.CertificateValidationContext;
import io.envoyproxy.envoy.extensions.transport_sockets.tls.v3.CommonTlsContext;
import io.grpc.Internal;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.xds.Bootstrapper.CertificateProviderInfo;
import io.grpc.xds.EnvoyServerProtoData.UpstreamTlsContext;
import io.grpc.xds.internal.sds.trust.SdsTrustManagerFactory;
import io.netty.handler.ssl.SslContextBuilder;
import java.security.cert.CertStoreException;
import java.security.cert.X509Certificate;
import java.util.Map;
import javax.annotation.Nullable;

/** A client SslContext provider using CertificateProviderInstance to fetch secrets. */
@Internal
public final class CertProviderClientSslContextProvider extends CertProviderSslContextProvider {

  private CertProviderClientSslContextProvider(
      Node node,
      @Nullable Map<String, CertificateProviderInfo> certProviders,
      CommonTlsContext.CertificateProviderInstance certInstance,
      CommonTlsContext.CertificateProviderInstance rootCertInstance,
      CertificateValidationContext staticCertValidationContext,
      UpstreamTlsContext upstreamTlsContext,
      CertificateProviderStore certificateProviderStore) {
    super(
        node,
        certProviders,
        certInstance,
        checkNotNull(rootCertInstance, "Client SSL requires rootCertInstance"),
        staticCertValidationContext,
        upstreamTlsContext,
        certificateProviderStore);
  }

  @Override
  protected final SslContextBuilder getSslContextBuilder(
          CertificateValidationContext certificateValidationContextdationContext)
      throws CertStoreException {
    SslContextBuilder sslContextBuilder =
        GrpcSslContexts.forClient()
            .trustManager(
                new SdsTrustManagerFactory(
                    savedTrustedRoots.toArray(new X509Certificate[0]),
                    certificateValidationContextdationContext));
    if (isMtls()) {
      sslContextBuilder.keyManager(savedKey, savedCertChain);
    }
    return sslContextBuilder;
  }

  /** Creates CertProviderClientSslContextProvider. */
  @Internal
  public static final class Factory {
    private static final Factory DEFAULT_INSTANCE =
        new Factory(CertificateProviderStore.getInstance());
    private final CertificateProviderStore certificateProviderStore;

    @VisibleForTesting public Factory(CertificateProviderStore certificateProviderStore) {
      this.certificateProviderStore = certificateProviderStore;
    }

    public static Factory getInstance() {
      return DEFAULT_INSTANCE;
    }

    /** Creates a {@link CertProviderClientSslContextProvider}. */
    public CertProviderClientSslContextProvider getProvider(
        UpstreamTlsContext upstreamTlsContext,
        Node node,
        @Nullable Map<String, CertificateProviderInfo> certProviders) {
      checkNotNull(upstreamTlsContext, "upstreamTlsContext");
      CommonTlsContext commonTlsContext = upstreamTlsContext.getCommonTlsContext();
      CertificateValidationContext staticCertValidationContext = getStaticValidationContext(
          commonTlsContext);
      CommonTlsContext.CertificateProviderInstance rootCertInstance = getRootCertProviderInstance(
          commonTlsContext);
      CommonTlsContext.CertificateProviderInstance certInstance = getCertProviderInstance(
          commonTlsContext);
      return new CertProviderClientSslContextProvider(
          node,
          certProviders,
          certInstance,
          rootCertInstance,
          staticCertValidationContext,
          upstreamTlsContext,
          certificateProviderStore);
    }
  }
}
