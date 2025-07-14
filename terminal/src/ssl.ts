import * as fs from 'fs'
import { SSLOptions } from "./globals"
import { logger } from './logger'

/*
 * All specified in deployment resource
 */
const sslKey = process.env.SYNAPSE_SSL_KEY || ''
const sslCertificate = process.env.SYNAPSE_SSL_CERTIFICATE || ''
const sslCertificateCA = process.env.SYNAPSE_SSL_CERTIFICATE_CA || ''

// TODO not sure if we will need these?
const sslProxyKey = process.env.SYNAPSE_SSL_PROXY_KEY || ''
const sslProxyCertificate = process.env.SYNAPSE_SSL_PROXY_CERTIFICATE || ''

function checkEnvVar(envVar: string, item: string) {
  if (!envVar || envVar.length === 0) {
    logger.error(`An ${item} is required but has not been specified`)
    process.exit(1)
  }

  if (!fs.existsSync(envVar)) {
    logger.error(`The ${item} assigned at "${envVar}" does not exist`)
    process.exit(1)
  }
}

export function configureSSLSupport(): SSLOptions | null {
  if (sslCertificate.length === 0) {
    return null // SSL not supported
  }

  checkEnvVar(sslKey, 'SSL Certifcate Key')
  checkEnvVar(sslCertificate, 'SSL Certifcate')
  checkEnvVar(sslCertificateCA, 'SSL Certifcate Authority')
  // checkEnvVar(sslProxyKey, 'SSL Proxy Certifcate Key')
  // checkEnvVar(sslProxyCertificate, 'SSL Proxy Certifcate')

  return {
    ca: fs.readFileSync(sslCertificateCA),
    key: fs.readFileSync(sslKey),
    cert: fs.readFileSync(sslCertificate),
  }
}
