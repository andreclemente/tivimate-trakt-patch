const TRAKT_API = 'https://api.trakt.tv';
const REDIRECT_URI = 'https://andreclemente.github.io/tivimate-trakt-patch/oauth/callback/';
const WORKER_BUILD = '2026-07-15-auth-bootstrap-v1';
const MAX_AUTH_REQUEST_BYTES = 16 * 1024;
const MAX_OAUTH_RESPONSE_BYTES = 1024 * 1024;
const JSON_HEADERS = {
  'content-type': 'application/json; charset=utf-8',
  'cache-control': 'no-store',
  'x-content-type-options': 'nosniff',
  'x-tivimate-trakt-worker-build': WORKER_BUILD,
};

function json(status, value) {
  return new Response(JSON.stringify(value), { status, headers: JSON_HEADERS });
}

class PayloadTooLarge extends Error {}

async function boundedText(message, maximumBytes) {
  if (!message.body) return '';
  const reader = message.body.getReader();
  const decoder = new TextDecoder();
  let bytes = 0;
  let text = '';
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      bytes += value.byteLength;
      if (bytes > maximumBytes) {
        await reader.cancel();
        throw new PayloadTooLarge();
      }
      text += decoder.decode(value, { stream: true });
    }
    return text + decoder.decode();
  } finally {
    reader.releaseLock();
  }
}

async function body(request) {
  const text = await boundedText(request, MAX_AUTH_REQUEST_BYTES);
  try {
    const value = JSON.parse(text);
    return value && typeof value === 'object' ? value : null;
  } catch {
    return null;
  }
}

function nonEmptyString(value) {
  return typeof value === 'string' && value.length > 0 && value.length <= 4096;
}

async function traktFetch(path, payload, fetchImpl, publicClientId) {
  const upstream = await fetchImpl(`${TRAKT_API}${path}`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      accept: 'application/json',
      'user-agent': 'TiviMate-Trakt-Patch-OAuth-Proxy/1.0',
      'trakt-api-version': '2',
    },
    body: JSON.stringify(payload),
  });
  let text;
  try {
    text = await boundedText(upstream, MAX_OAUTH_RESPONSE_BYTES);
  } catch (error) {
    if (error instanceof PayloadTooLarge) {
      return json(502, { error: 'upstream_response_too_large' });
    }
    throw error;
  }
  if (upstream.status >= 200 && upstream.status < 300 && publicClientId) {
    try {
      const value = JSON.parse(text);
      if (value && typeof value === 'object' && !Array.isArray(value)) {
        value.client_id = publicClientId;
        text = JSON.stringify(value);
      }
    } catch {
      // Preserve an unexpected successful upstream body unchanged.
    }
  }
  const responseBody = upstream.status === 204 || upstream.status === 205 ? null : text;
  return new Response(responseBody, {
    status: upstream.status,
    headers: {
      ...JSON_HEADERS,
      'content-type': upstream.headers.get('content-type') || 'application/json; charset=utf-8',
    },
  });
}

export async function handle(request, env, fetchImpl = fetch) {
  const path = new URL(request.url).pathname;
  if (path === '/v1/client' && request.method === 'GET') {
    if (!env.TRAKT_CLIENT_ID) return json(500, { error: 'server_not_configured' });
    return json(200, { client_id: env.TRAKT_CLIENT_ID });
  }
  if (request.method !== 'POST') return json(404, { error: 'not_found' });
  if (!env.TRAKT_CLIENT_ID || !env.TRAKT_CLIENT_SECRET) {
    return json(500, { error: 'server_not_configured' });
  }

  if (path === '/v1/device/code') {
    return traktFetch('/oauth/device/code', { client_id: env.TRAKT_CLIENT_ID }, fetchImpl);
  }

  if (path !== '/v1/device/token' && path !== '/v1/token' && path !== '/v1/revoke') {
    return json(404, { error: 'not_found' });
  }
  let requestBody;
  try {
    requestBody = await body(request);
  } catch (error) {
    if (error instanceof PayloadTooLarge) return json(413, { error: 'request_too_large' });
    throw error;
  }
  if (path === '/v1/device/token') {
    if (!requestBody || !nonEmptyString(requestBody.code)) {
      return json(400, { error: 'invalid_request' });
    }
    return traktFetch('/oauth/device/token', {
      code: requestBody.code,
      client_id: env.TRAKT_CLIENT_ID,
      client_secret: env.TRAKT_CLIENT_SECRET,
    }, fetchImpl, env.TRAKT_CLIENT_ID);
  }

  if (path === '/v1/revoke') {
    if (!requestBody || !nonEmptyString(requestBody.token)) {
      return json(400, { error: 'invalid_request' });
    }
    return traktFetch('/oauth/revoke', {
      token: requestBody.token,
      client_id: env.TRAKT_CLIENT_ID,
      client_secret: env.TRAKT_CLIENT_SECRET,
    }, fetchImpl);
  }

  if (path === '/v1/token') {
    if (!requestBody || !nonEmptyString(requestBody.refresh_token)) {
      return json(400, { error: 'invalid_request' });
    }
    return traktFetch('/oauth/token', {
      refresh_token: requestBody.refresh_token,
      client_id: env.TRAKT_CLIENT_ID,
      client_secret: env.TRAKT_CLIENT_SECRET,
      redirect_uri: REDIRECT_URI,
      grant_type: 'refresh_token',
    }, fetchImpl, env.TRAKT_CLIENT_ID);
  }
}

export default { fetch: (request, env) => handle(request, env) };
