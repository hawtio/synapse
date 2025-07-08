/* jshint node: true */
import express, { Express } from 'express'
import helmet from 'helmet'
import methodOverride from 'method-override'
import cors from 'cors'
import * as https from 'https'
import { v4 as uuidv4 } from 'uuid'
import { logger, expressLogger } from './logger'
import { configureSSLSupport } from './ssl'
import { initModel, invoke } from './model-provider'
import { ModelConfiguration } from './globals'

async function startServer() {
  /*
   * Running mode of server
   */
  const environment = process.env.NODE_ENV ?? 'development'

  /*
   * Server settings
   */
  const port = process.env.SYNAPSE_APP_PORT ?? 3000
  const sslSupport = configureSSLSupport()

  /*
   * Large Language Model Settings
   */
  const provider = process.env.LLM_PROVIDER ?? ''
  const name = process.env.LLM_NAME ?? ''
  const baseURL = process.env.LLM_BASE_URL
  const modelPath = process.env.LLM_MODEL_PATH

  const modelConfig: ModelConfiguration = {
    provider,
    name,
    temperature: 0.2,
    baseURL,
    modelPath
  }

  logger.info('**************************************')
  logger.info(`* Environment:       ${environment}`)
  logger.info(`* App Port:          ${port}`)
  logger.info(`* Log Level:         ${logger.level}`)
  logger.info(`* SSL Enabled:       ${sslSupport !== null}`)
  logger.info('* LLM Configuration: ', modelConfig)
  logger.info('**************************************')

  const model = await initModel(modelConfig)
  if (!model) {
    logger.error('Fatal: Could not initialize language model. Please check configuration. Exiting.')
    process.exit(1)
  }

  logger.info('Model initialized successfully.')

  const synapseServer: Express = express()

  // Log middleware requests
  synapseServer.use(expressLogger)

  /*
   * Heightens security providing headers
   *
   * - Sets X-Frame-Options: "SAMEORIGIN"
   */
  synapseServer.use(
    helmet({
      strictTransportSecurity: {
        maxAge: 31536000,
        includeSubDomains: true,
      },
      contentSecurityPolicy: {
        directives: {
          'default-src': 'self',
          'frame-ancestors': 'self',
          'form-action': 'self',
        },
      },
    }),
  )

  // Cross Origin Support
  synapseServer.use(
    cors({
      credentials: true,
    }),
  )

  // override with the X-HTTP-Method-Override header in the request. simulate DELETE/PUT
  synapseServer.use(methodOverride('X-HTTP-Method-Override'))

  /*
   * =================================================================
   * Configure the main routes for the API
   * =================================================================
   */
  synapseServer.get('/start_session', (req, res) => {
    const sessionId = uuidv4()
    res.json({ sessionId: sessionId })
  })

  /**
   * Provides the main invoke request for chatting to the model
   */
  synapseServer
    .route('/invoke')
      .get((req, res) => {
        res.setHeader('Content-Type', 'application/json')
        res.status(500).json({
          message: `Error (synapse): Access to ${req.url} is only as a POST request`,
        })
      })
      .post(express.json({ type: '*/json', limit: '50mb', strict: false }), async (req, res) => {
        invoke(model, req, res)
      })

  /**
   * Provide a status route for the server. Used for
   * establishing a heartbeat when installed on the cluster
   */
  synapseServer.route('/status').get((req, res) => {
    res.setHeader('Content-Type', 'application/json')
    res.status(200).json({ port: port, loglevel: logger.level, modelconfig: modelConfig })
  })

  /**
   * Default rule for anything else sent to the server
   */
  synapseServer.route('*').all((req, res) => {
    res.setHeader('Content-Type', 'application/json')
    res.status(502).json({
      message: `Error (synapse): Access to ${req.url} is not permitted.`,
    })
  })

  /*
   * Must use a wildcard for json Content-Type since jolokia
   * has request payloads with a Content-Type header value of
   * 'text/json' whereas express, by default, only uses
   * 'application/json'.
   *
   * Needs to be added last to avoid being overwritten by the proxy middleware
   */
   synapseServer.use(express.json({ type: '*/json', limit: '50mb', strict: false }))
   synapseServer.use(express.urlencoded({ extended: false }))

  /*
   * Exports the running server for use in unit testing
   */
  if (sslSupport) {
    const synapseHttpsServer = https.createServer(
      {
        ca: sslSupport.ca,
        key: sslSupport.key,
        cert: sslSupport.cert,
      },
      synapseServer,
    )

    synapseHttpsServer.listen(port, () => {
      logger.info(`HTTPS Synapse Server running on port ${port}`)
    })
  } else {
    synapseServer.listen(port, () => {
      logger.info(`INFO: Synapse Server listening on port ${port}`)
    })
  }
}

/*
 * ====================================================================
 * Execute the startServer function and handle any fatal startup errors
 * ====================================================================
 */
startServer()
  .catch(error => {
    logger.error('Failed to start synapse-host server:', error)
    process.exit(1)
  })
