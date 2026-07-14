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

async function authenticatedTraktFetch(path, method, payload, accessToken, env, fetchImpl) {
  const upstream = await fetchImpl(`${TRAKT_API}${path}`, {
    method,
    headers: {
      'content-type': 'application/json',
      accept: 'application/json',
      'user-agent': 'TiviMate-Trakt-Patch-OAuth-Proxy/1.0',
      'trakt-api-version': '2',
      'trakt-api-key': env.TRAKT_CLIENT_ID,
      authorization: `Bearer ${accessToken}`,
    },
    ...(payload ? { body: JSON.stringify(payload) } : {}),
  });
  return new Response(await upstream.text(), {
    status: upstream.status,
    headers: {
      ...JSON_HEADERS,
      'content-type': upstream.headers.get('content-type') || 'application/json; charset=utf-8',
    },
  });
}

function bearerToken(request) {
  const value = request.headers.get('authorization') || '';
  const match = /^Bearer (.+)$/.exec(value);
  return match && nonEmptyString(match[1]) ? match[1] : null;
}

function hasSyncItems(value) {
  return value && typeof value === 'object'
    && ((Array.isArray(value.movies) && value.movies.length > 0)
      || (Array.isArray(value.shows) && value.shows.length > 0));
}

function validScrobble(value) {
  return value && typeof value === 'object'
    && Number.isFinite(value.progress) && value.progress >= 0 && value.progress <= 100
    && ((value.movie && typeof value.movie === 'object')
      || (value.show && typeof value.show === 'object' && value.episode && typeof value.episode === 'object'));
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
  const syncRoutes = {
    '/v1/sync/history': '/sync/history',
    '/v1/sync/history/remove': '/sync/history/remove',
  };
  const scrobbleRoutes = {
    '/v1/scrobble/start': '/scrobble/start',
    '/v1/scrobble/pause': '/scrobble/pause',
    '/v1/scrobble/stop': '/scrobble/stop',
  };
  if (syncRoutes[path] || scrobbleRoutes[path] || path === '/v1/sync/status'
      || path === '/v1/sync/playback') {
    const accessToken = bearerToken(request);
    if (!accessToken) return json(401, { error: 'missing_authorization' });
    if (syncRoutes[path]) {
      if (!hasSyncItems(requestBody)) return json(400, { error: 'invalid_sync_payload' });
      return authenticatedTraktFetch(syncRoutes[path], 'POST', requestBody, accessToken, env, fetchImpl);
    }
    if (scrobbleRoutes[path]) {
      if (!validScrobble(requestBody)) return json(400, { error: 'invalid_scrobble_payload' });
      return authenticatedTraktFetch(scrobbleRoutes[path], 'POST', requestBody, accessToken, env, fetchImpl);
    }
    return authenticatedTraktFetch(path === '/v1/sync/status' ? '/users/settings' : '/sync/playback',
      'GET', null, accessToken, env, fetchImpl);
  }

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
