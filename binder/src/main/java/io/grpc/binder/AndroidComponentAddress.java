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

package io.grpc.binder;

import static android.content.Intent.URI_ANDROID_APP_SCHEME;
import static com.google.common.base.Preconditions.checkArgument;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import io.grpc.ExperimentalApi;
import java.net.SocketAddress;

/**
 * The target of an Android {@link android.app.Service} binding.
 *
 * <p>Consists of a {@link ComponentName} reference to the Service and the action, data URI, type,
 * and category set for an {@link Intent} used to bind to it. All together, these fields identify
 * the {@link android.os.IBinder} that would be returned by some implementation of {@link
 * android.app.Service#onBind(Intent)}. Indeed, the semantics of {@link #equals(Object)} match
 * Android's internal equivalence relation for caching the result of calling this method. See <a
 * href="https://developer.android.com/guide/components/bound-services">Bound Services Overview</a>
 * for more.
 *
 * <p>For convenience in the common case where a {@link android.app.Service} exposes just one {@link
 * android.os.IBinder} IPC interface, we provide default values for the binding {@link Intent}
 * fields, namely, an action of {@link ApiConstants#ACTION_BIND}, an empty category set and null
 * type and data URI.
 */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/8022")
public class AndroidComponentAddress extends SocketAddress { // NOTE: Only temporarily non-final.
  private static final long serialVersionUID = 0L;

  private final Intent bindIntent; // An "explicit" Intent. In other words, getComponent() != null.

  protected AndroidComponentAddress(Intent bindIntent) {
    checkArgument(bindIntent.getComponent() != null, "Missing required component");
    this.bindIntent = bindIntent;
  }

  /**
   * Creates an address for the given {@link android.app.Service} instance with the default binding
   * {@link Intent}.
   */
  public static AndroidComponentAddress forContext(Context context) {
    return forLocalComponent(context, context.getClass());
  }

  /**
   * Creates an address referencing a {@link android.app.Service} hosted by this application and
   * using the default binding {@link Intent}.
   */
  public static AndroidComponentAddress forLocalComponent(Context context, Class<?> cls) {
    return forComponent(new ComponentName(context, cls));
  }

  /**
   * Creates an address referencing a {@link android.app.Service} (potentially) in another
   * application and using the default binding {@link Intent}.
   */
  public static AndroidComponentAddress forRemoteComponent(String packageName, String className) {
    return forComponent(new ComponentName(packageName, className));
  }

  /**
   * Creates a new address that refers to <code>intent</code>'s component and that uses the "filter
   * matching" fields of <code>intent</code> as the binding {@link Intent}.
   *
   * <p>A multi-tenant {@link android.app.Service} can call this from its {@link
   * android.app.Service#onBind(Intent)} method to locate an appropriate {@link io.grpc.Server} by
   * listening address.
   *
   * @throws IllegalArgumentException if intent's component is null
   */
  public static AndroidComponentAddress forBindIntent(Intent intent) {
    return new AndroidComponentAddress(intent.cloneFilter());
  }

  /**
   * Creates an address referencing the specified {@link android.app.Service} component and using
   * the default binding {@link Intent}.
   */
  public static AndroidComponentAddress forComponent(ComponentName component) {
    return new AndroidComponentAddress(
        new Intent(ApiConstants.ACTION_BIND).setComponent(component));
  }

  public String getAuthority() {
    return getComponent().getPackageName();
  }

  public ComponentName getComponent() {
    return bindIntent.getComponent();
  }

  /**
   * Returns this address as an explicit {@link Intent} suitable for passing to {@link
   * Context#bindService}.
   */
  public Intent asBindIntent() {
    return bindIntent.cloneFilter(); // Intent is mutable so return a copy.
  }

  /**
   * Returns this address as an "android-app://" uri.
   */
  public String asAndroidAppUri() {
    return bindIntent.toUri(URI_ANDROID_APP_SCHEME);
  }

  @Override
  public int hashCode() {
    return bindIntent.filterHashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof AndroidComponentAddress) {
      AndroidComponentAddress that = (AndroidComponentAddress) obj;
      return bindIntent.filterEquals(that.bindIntent);
    }
    return false;
  }

  @Override
  public String toString() {
    return "AndroidComponentAddress[" + bindIntent + "]";
  }
}
