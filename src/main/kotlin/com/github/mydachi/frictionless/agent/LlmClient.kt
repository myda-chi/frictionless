package com.github.mydachi.frictionless.agent

/**
 * Deliverable G1: the seam between the loop and whatever answers it.
 *
 * The loop in [CharacterisationTestAgent] is the valuable part and it is fully testable against a
 * fake, so nothing about G3 depends on a network, a key, or Koog's API surface.
 */
interface LlmClient {

    /** One completion. Implementations block; callers are already off the EDT. */
    fun complete(systemPrompt: String, userPrompt: String): String

    /** False when no key is configured, so the caller can fall back rather than throw on stage. */
    val isConfigured: Boolean
}
