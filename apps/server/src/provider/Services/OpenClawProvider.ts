import { Context } from "effect";

import type { ServerProviderShape } from "./ServerProvider.ts";

export interface OpenClawProviderShape extends ServerProviderShape {}

export class OpenClawProvider extends Context.Service<OpenClawProvider, OpenClawProviderShape>()(
  "t3/provider/Services/OpenClawProvider",
) {}
