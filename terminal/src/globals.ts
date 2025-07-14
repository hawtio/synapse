
/**
 * A map of default model names for each supported provider.
 * These are chosen as good, general-purpose defaults.
 */
export const defaultModelNames: { [key: string]: string } = {
  google: 'gemini-1.5-flash-latest',
  openai: 'gpt-4o-mini',
  mistral: 'mistral-large-latest',
  anthropic: 'claude-3-5-sonnet-20240620',
  cohere: 'command-r',
}

export interface SSLOptions {
  ca: Buffer
  key: Buffer
  cert: Buffer
}

export interface ModelConfiguration {
  // The provider of the model
  // eg. google, openai, openshift.ai
  provider: string,

  // This will be the model name,
  // e.g., "gemini-1.5-flash" or "mistralai/Mistral-7B..."
  name: string,

  // The api key for access to the LLM
  // (if required, eg. google, openai)
  apiKey?: string

  temperature?: number,

  // Only used for OpenAI-compatible endpoints
  baseURL?: string

  // For locally-run models, e.g., "./models/my-model.gguf"
  modelPath?: string
}
