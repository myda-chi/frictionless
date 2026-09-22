package com.github.mydachi.frictionless.agent

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.runBlocking

/**
 * Deliverable G1: Koog wiring.
 *
 * Koog is JetBrains' own Kotlin agent framework and is what the brief points at. The key comes from
 * the environment, never from a committed file.
 *
 * The multi-step reasoning lives in [CharacterisationTestAgent], not here: this is one round trip.
 * That split is deliberate — the loop is what has to be right, and it stays testable without a key.
 */
class KoogLlmClient(
    private val apiKey: String? = resolveKey(),
) : LlmClient {

    override val isConfigured: Boolean get() = !apiKey.isNullOrBlank()

    private val executor by lazy {
        MultiLLMPromptExecutor(listOf(OpenAILLMClient(requireNotNull(apiKey))))
    }

    override fun complete(systemPrompt: String, userPrompt: String): String {
        require(isConfigured) { "$API_KEY_ENV is not set" }
        return try {
            runBlocking {
                executor.execute(
                    prompt = prompt("frictionless-characterisation-test") {
                        system(systemPrompt)
                        user(userPrompt)
                    },
                    model = OpenAIModels.Chat.GPT4o,
                ).textContent()
            }
        } catch (e: Exception) {
            thisLogger().warn("Koog call failed", e)
            throw e
        }
    }

    companion object {
        const val API_KEY_ENV = "OPENAI_API_KEY"

        /** `~/.frictionless/openai-key`, one line, no quotes. */
        val KEY_FILE: java.nio.file.Path =
            java.nio.file.Paths.get(System.getProperty("user.home"), ".frictionless", "openai-key")

        /**
         * The environment first, then a key file in the home directory.
         *
         * The file exists because the environment is the fragile half on a demo machine: the sandbox
         * IDE inherits the Gradle daemon's environment, not the shell's, so a key exported after the
         * daemon started never arrives. A file is read at the moment it is needed and survives
         * restarts, daemons and a borrowed laptop.
         *
         * Neither is ever committed — the environment is per-shell and the file lives outside the
         * repository.
         */
        fun resolveKey(): String? {
            System.getenv(API_KEY_ENV)?.takeIf { it.isNotBlank() }?.let { return it }
            return runCatching { java.nio.file.Files.readString(KEY_FILE).trim() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
    }
}
