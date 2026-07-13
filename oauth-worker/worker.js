const TRAKT_API = 'https://api.trakt.tv';
const REDIRECT_URI = 'https://andreclemente.github.io/tivimate-trakt-patch/oauth/callback/';
const JSON_HEADERS = {
  'content-type': 'application/json; charset=utf-8',
  'cache-control': 'no-store',
  'x-content-type-options': 'nosniff',
};

function json(status, value) {
  return new Response(JSON.stringify(value), { status, headers: JSON_HEADERS });
}

async function body(request) {
  try {
    const value = await request.json();
    return value && typeof value === 'object' ? value : null;
  } catch {
    return null;
  }
}

function nonEmptyString(value) {
  return typeof value === 'string' && value.length > 0 && value.length <= 4096;
}

async function traktFetch(path, payload, fetchImpl) {
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
  return new Response(await upstream.text(), {
    status: upstream.status,
    headers: {
      ...JSON_HEADERS,
      'content-type': upstream.headers.get('content-type') || 'application/json; charset=utf-8',
    },
  });
}

export async function handle(request, env, fetchImpl = fetch) {
  if (request.method !== 'POST') return json(404, { error: 'not_found' });
  if (!env.TRAKT_CLIENT_ID || !env.TRAKT_CLIENT_SECRET) {
    return json(500, { error: 'server_not_configured' });
  }

  const path = new URL(request.url).pathname;
  if (path === '/v1/device/code') {
    return traktFetch('/oauth/device/code', { client_id: env.TRAKT_CLIENT_ID }, fetchImpl);
  }

  const requestBody = await body(request);
  if (path === '/v1/device/token') {
    if (!requestBody || !nonEmptyString(requestBody.code)) {
      return json(400, { error: 'invalid_request' });
    }
    return traktFetch('/oauth/device/token', {
      code: requestBody.code,
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
    }, fetchImpl);
  }

  return json(404, { error: 'not_found' });
}

export default { fetch: (request, env) => handle(request, env) };
