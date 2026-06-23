/**
 * PiAdapter - shape type for the Pi provider adapter.
 *
 * The driver model bundles one adapter per configured Pi instance, so this
 * module only retains the shape interface as a naming anchor.
 *
 * @module PiAdapter
 */
import type { ProviderAdapterError } from "../Errors.ts";
import type { ProviderAdapterShape } from "./ProviderAdapter.ts";

export interface PiAdapterShape extends ProviderAdapterShape<ProviderAdapterError> {}
