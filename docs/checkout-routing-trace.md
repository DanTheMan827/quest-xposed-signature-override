# Checkout identity trace

Verified from the supplied Horizon smali:

1. `OVRService$4.getIAPCheckoutFlowIntent(String, Bundle)` constructs
   `IAPCheckoutFlowResultBuilder(service, firstStringArgument)` and calls
   `ResultBuilderNoBundle.A00(Bundle)`.
2. The builder wraps itself in `ResultBuilderNoBundle$1`. Its `A00` calls the
   common `ResultBuilder.A00`, then returns `mValue` or raises the original
   `RemoteException` if the builder recorded an error.
3. `ResultBuilder.A00` calls `CallerUtils.getCallingPackage(Bundle)` before the
   checkout branch. The caller resolver's UID checks still run before any alias.
4. The checkout branch gets the normal shell launch intent, sets the shell launch
   action and selects the Store IAP activity or `systemux://launch_iap`, depending
   on the platform feature flag.
5. With the supported empty offer-ID argument, the branch reads the requested
   `sku` from the original bundle. A missing SKU or unsupported offer ID still
   raises the original error.
6. `X.003.A0e(Bundle, provider, package)` resolves the app ID through the shared
   `ExternalPlatformLocal` library lookup. The branch sets `app_id` and the
   `/iap/<appId>/<sku>/` URI extra on the normal intent, then stores it as `mValue`.

The module previously excluded this service entry and treated the checkout
wrapper as an unrelated nested builder. Version 3.13 allows identity translation
at both boundaries. The existing one-pass frame prevents a destination that is
also a source in another mapping from being translated a second time.

No intent rewriting, callback-component substitution, SKU changes, automatic
retry, payment-state change or fabricated purchase response is added. Real source
UID validation and the normal Store purchase flow remain intact. Checkout logging
records only entry/completion, package translation, exception type and whether an
intent was returned; it does not log the SKU, intent extras, credentials or payment
data.

The reported first-attempt failure has not been reproduced here. The shared
library hook already resolved the destination app ID before this change, and the
Store implementation is not part of the supplied Horizon files. Matching intent
success on both attempts would place the next investigation after Horizon's
checkout builder, rather than establish a need for a retry inside the module.
