import assert from 'node:assert/strict';
import test from 'node:test';

const { default: worker, handle } = await import('../worker.js');

const env = {
  TRAKT_CLIENT_ID: 'public-client-id',
  TRAKT_CLIENT_SECRET: 'private-client-secret',
};

async function invoke(path, body) {
  let forwarded;
  const response = await handle(
    new Request(`https://proxy.example${path}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    }),
    env,
    async (url, init) => {
      forwarded = new Request(url, init);
      return new Response(JSON.stringify({ access_token: 'token' }), {
        headers: { 'content-type': 'application/json' },
      });
    },
  );
  return { response, forwarded };
}

test('device code request exposes no secret to the TV client', async () => {
  let forwarded;
  const response = await handle(
    new Request('https://proxy.example/v1/device/code', { method: 'POST' }),
    env,
    async (url, init) => {
      forwarded = new Request(url, init);
      return new Response(JSON.stringify({ user_code: 'ABCD-1234' }), {
        headers: { 'content-type': 'application/json' },
      });
    },
  );
  assert.equal(response.status, 200);
  assert.equal(forwarded.url, 'https://api.trakt.tv/oauth/device/code');
  assert.deepEqual(await forwarded.json(), { client_id: 'public-client-id' });
});

test('device token exchange adds server-side client credentials only', async () => {
  const { response, forwarded } = await invoke('/v1/device/token', { code: 'device-code' });
  assert.equal(response.status, 200);
  assert.equal(forwarded.url, 'https://api.trakt.tv/oauth/device/token');
  assert.deepEqual(await forwarded.json(), {
    code: 'device-code',
    client_id: 'public-client-id',
    client_secret: 'private-client-secret',
  });
  assert.equal(response.headers.get('cache-control'), 'no-store');
});

test('refresh endpoint only accepts a refresh token from the device', async () => {
  const { response, forwarded } = await invoke('/v1/token', { refresh_token: 'refresh-token' });
  assert.equal(response.status, 200);
  assert.equal(forwarded.url, 'https://api.trakt.tv/oauth/token');
  assert.deepEqual(await forwarded.json(), {
    refresh_token: 'refresh-token',
    client_id: 'public-client-id',
    client_secret: 'private-client-secret',
    redirect_uri: 'https://andreclemente.github.io/tivimate-trakt-patch/oauth/callback/',
    grant_type: 'refresh_token',
  });
});

test('authenticated history sync forwards the bearer token only as an upstream header', async () => {
  let forwarded;
  const response = await handle(
    new Request('https://proxy.example/v1/sync/history', {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: 'Bearer user-access-token' },
      body: JSON.stringify({ movies: [{ title: 'Example', year: 2026 }] }),
    }),
    env,
    async (url, init) => {
      forwarded = new Request(url, init);
      return new Response('{}', { headers: { 'content-type': 'application/json' } });
    },
  );
  assert.equal(response.status, 200);
  assert.equal(forwarded.url, 'https://api.trakt.tv/sync/history');
  assert.equal(forwarded.headers.get('authorization'), 'Bearer user-access-token');
  assert.equal(forwarded.headers.get('trakt-api-key'), 'public-client-id');
  assert.deepEqual(await forwarded.json(), { movies: [{ title: 'Example', year: 2026 }] });
});

test('imports current watched movies without exposing client credentials', async () => {
  let forwarded;
  const response = await handle(new Request('https://proxy.example/v1/import/watched/movies', {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: 'Bearer user-access-token' },
    body: '{}',
  }), env, async (url, init) => {
    forwarded = new Request(url, init);
    return new Response(JSON.stringify([{ movie: { ids: { tmdb: 42 } } }]), {
      headers: { 'content-type': 'application/json' },
    });
  });
  assert.equal(response.status, 200);
  assert.equal(forwarded.url, 'https://api.trakt.tv/sync/watched/movies');
  assert.equal(forwarded.method, 'GET');
  assert.equal(forwarded.headers.get('authorization'), 'Bearer user-access-token');
  assert.equal(await forwarded.text(), '');
});

test('imports current watched shows and partial playback', async () => {
  const paths = [];
  for (const path of ['/v1/import/watched/shows', '/v1/import/playback']) {
    const response = await handle(new Request(`https://proxy.example${path}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: 'Bearer user-access-token' },
      body: '{}',
    }), env, async (url, init) => {
      const forwarded = new Request(url, init);
      paths.push({ url: forwarded.url, method: forwarded.method, body: await forwarded.text() });
      return new Response('[]', { headers: { 'content-type': 'application/json' } });
    });
    assert.equal(response.status, 200);
  }
  assert.deepEqual(paths, [
    { url: 'https://api.trakt.tv/sync/watched/shows', method: 'GET', body: '' },
    { url: 'https://api.trakt.tv/sync/playback', method: 'GET', body: '' },
  ]);
});

test('sync routes reject a missing bearer token', async () => {
  const response = await worker.fetch(new Request('https://proxy.example/v1/sync/history', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ movies: [] }),
  }), env);
  assert.equal(response.status, 401);
});

test('rejects unsupported routes and malformed device requests', async () => {
  const unsupported = await worker.fetch(new Request('https://proxy.example/nope'), env);
  assert.equal(unsupported.status, 404);
  const malformed = await worker.fetch(new Request('https://proxy.example/v1/device/token', { method: 'POST' }), env);
  assert.equal(malformed.status, 400);
});
