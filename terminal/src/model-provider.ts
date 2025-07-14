import { ChatPromptTemplate, MessagesPlaceholder } from '@langchain/core/prompts'
import { ModelConfiguration, defaultModelNames } from './globals'
import { StringOutputParser } from '@langchain/core/output_parsers'
import { BaseChatModel } from '@langchain/core/language_models/chat_models'
import { FileSystemChatMessageHistory } from '@langchain/community/stores/message/file_system'
import { Request as ExpressRequest, Response as ExpressResponse } from 'express-serve-static-core'
import { isError } from './utils'
import { RunnableWithMessageHistory } from '@langchain/core/runnables'
import { logger } from './logger'

// Define the output parser
const outputParser = new StringOutputParser()

function getModelName(provider: string, chosenName?: string): string {
  // If the user provided a specific name, always use it.
  if (chosenName && chosenName.length > 0) {
    return chosenName
  }

  return defaultModelNames[provider] ?? ''
}

async function getModel(config: ModelConfiguration): Promise<BaseChatModel | null> {
  const { provider, apiKey, temperature, baseURL, modelPath } = config
  const name = getModelName(config.provider, config.name)

  if (!name || name.length === 0) {
    console.error(`Cannot find a model name to use for the provider ${provider}. Please specify the LLM_NAME env var`)
    return null
  }

  console.log(`Using ${provider} model ${name}`)

  switch (provider) {
    case 'google':
      if (!apiKey) {
        logger.error('API_KEY not set. Cannot initialize Google Gemini model.')
        return null
      }
      const { ChatGoogleGenerativeAI } = await import('@langchain/google-genai')
      return new ChatGoogleGenerativeAI({
        apiKey: apiKey,
        model: name,
        temperature
      })
    case 'openai':
      if (!apiKey) {
        logger.error('API_KEY not set. Cannot initialize OpenAI model.')
        return null
      }
      const { ChatOpenAI } = await import ('@langchain/openai')
      return new ChatOpenAI({
        apiKey: apiKey,
        model: name,
        temperature
      })
    case 'mistral':
      if (!apiKey) {
        logger.error('API_KEY not set. Cannot initialize Mistral model.')
        return null
      }
      const { ChatMistralAI } = await import ('@langchain/mistralai')
      return new ChatMistralAI({
        apiKey: apiKey,
        model: name,
        temperature,
      })
    case 'openai-compatible':
      if (!baseURL || baseURL.length === 0) {
        logger.error("A 'baseURL' is required for the 'openai-compatible' provider.")
        return null
      }
      if (!name) {
        logger.error("A model 'name' is required for the 'openai-compatible' provider.")
        return null
      }
      // This uses the OpenAI client to talk to an internal, compatible API
      const { ChatOpenAI: ChatOpenAICompatible } = await import("@langchain/openai")
      return new ChatOpenAICompatible(
        {
          // Model Parameters
          modelName: name,
          temperature: temperature ?? 0, // Default to 0 if not provided
          openAIApiKey: 'EMPTY', // No real key needed for internal service
          // Connection parameters go inside a 'configuration' object
          configuration: {
            baseURL: baseURL,
          },
        }
      )
    case 'ollama':
      if (!baseURL) {
        logger.error("A 'baseURL' is required for the 'ollama' provider (e.g., http://localhost:11434).")
        return null
      }
      try {
        const { ChatOllama } = await import('@langchain/ollama')
        return new ChatOllama({
          baseUrl: baseURL,
          model: name,
          temperature,
        })
      } catch (e) {
        logger.error('Failed to initialize Ollama model. Have you installed "@langchain/community"?')
        logger.error(e)
        return null
      }
    // case 'llama.cpp':
    //   if (!modelPath) {
    //     logger.error("A 'modelPath' is required for the 'llama.cpp' provider.")
    //     return null
    //   }
    //   try {
    //     const { ChatLlamaCpp } = await import('@langchain/community/chat_models/llama_cpp')
    //
    //     logger.info(`Loading local model from path: ${modelPath}`)
    //     return new ChatLlamaCpp({
    //       modelPath: modelPath,
    //       temperature: temperature ?? 0.7, // A sensible default for local models
    //     })
    //   } catch (e) {
    //     logger.error('Failed to initialize Llama.cpp model.')
    //     logger.error(e)
    //     return null
    //   }
    default:
      logger.error(`Unknown LLM provider specified: '${provider}'`)
      return null
  }
}

export async function initModel(modelConfig: ModelConfiguration): Promise<BaseChatModel> {
  // Get the selected model from our factory
  const llModel = await getModel(modelConfig)
  if (!llModel) {
    throw new Error('Error: Cannot configure an LLM model provider')
  }

  return llModel
}

export async function invoke(llModel: BaseChatModel, req: ExpressRequest, res: ExpressResponse) {
  try {
    logger.trace(`Request: `, req.body)

    if (!req.body || Object.keys(req.body).length === 0) {
      return res.status(400).json({ error: 'Request body is empty. Input and sessionId are required.' })
    }

    const { input, sessionId } = req.body

    if (!input || !sessionId) {
      return res.status(400).json({ error: 'Both Input and sessionId are required.' })
    }

    const prompt = ChatPromptTemplate.fromMessages([
      ['system', 'You are a helpful assistant. Answer all questions to the best of your ability.'],
      new MessagesPlaceholder('chat_history'), // Use the placeholder
      ['human', '{input}'],
    ])

    const runnable = prompt.pipe(llModel).pipe(outputParser)

    const chainWithHistory = new RunnableWithMessageHistory({
      runnable: runnable,
      inputMessagesKey: 'input',
      historyMessagesKey: 'chat_history',
      getMessageHistory: async (sessionId) => {
        const chatHistory = new FileSystemChatMessageHistory({
          sessionId,
          userId: 'user-id',
        })
        return chatHistory
      },
    })

    const result = await chainWithHistory.invoke(
      { input: input },
      { configurable: { sessionId: 'langchain-test-session' } }
    )

    return res.json({ response: result })

  } catch (error) {
    console.error('Error in /invoke:', error)
    return res.status(500).json({ error: `An error occurred. Details: ${isError(error) ? error.message : error}` })
  }
}
