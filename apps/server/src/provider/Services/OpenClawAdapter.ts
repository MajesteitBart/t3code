import { Context } from "effect";

import type { ProviderAdapterError } from "../Errors.ts";
import type { ProviderAdapterShape } from "./ProviderAdapter.ts";

export interface OpenClawAdapterShape extends ProviderAdapterShape<ProviderAdapterError> {
  readonly provider: "openclaw";
}

export class OpenClawAdapter extends Context.Service<OpenClawAdapter, OpenClawAdapterShape>()(
  "t3/provider/Services/OpenClawAdapter",
) {}
