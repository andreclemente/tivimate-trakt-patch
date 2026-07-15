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
      return new Response(JSON.stringify({ access_token: 'token', refresh_token: 'refresh' }), {
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

test('client bootstrap exposes only the public client id and needs no secret', async () => {
  const response = await handle(
    new Request('https://proxy.example/v1/client'),
    { TRAKT_CLIENT_ID: 'public-client-id' },
    async () => { throw new Error('must not call upstream'); },
  );
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { client_id: 'public-client-id' });
  assert.equal(response.headers.get('cache-control'), 'no-store');
});

test('device token exchange adds server-side client credentials and returns public id', async () => {
  const { response, forwarded } = await invoke('/v1/device/token', { code: 'device-code' });
  assert.equal(response.status, 200);
  assert.equal(forwarded.url, 'https://api.trakt.tv/oauth/device/token');
  assert.deepEqual(await forwarded.json(), {
    code: 'device-code',
    client_id: 'public-client-id',
    client_secret: 'private-client-secret',
  });
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.equal((await response.json()).client_id, 'public-client-id');
});

test('refresh endpoint only accepts a refresh token and returns public id', async () => {
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
  assert.equal((await response.json()).client_id, 'public-client-id');
});

test('rejects an oversized chunked auth request with 413 before forwarding', async () => {
  let forwarded = false;
  const oversized = new ReadableStream({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(`{"code":"${'x'.repeat(9000)}`));
      controller.enqueue(new TextEncoder().encode(`${'x'.repeat(9000)}"}`));
      controller.close();
    },
  });
  const response = await handle(new Request('https://proxy.example/v1/device/token', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: oversized, duplex: 'half',
  }), env, async () => { forwarded = true; return new Response('{}'); });
  assert.equal(response.status, 413);
  assert.deepEqual(await response.json(), { error: 'request_too_large' });
  assert.equal(forwarded, false);
});

test('replaces an oversized chunked OAuth response with a body-safe 502', async () => {
  let cancelled = false;
  const response = await handle(new Request('https://proxy.example/v1/token', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ refresh_token: 'refresh-token' }),
  }), env, async () => new Response(new ReadableStream({
    pull(controller) {
      controller.enqueue(new Uint8Array(600000));
      controller.enqueue(new Uint8Array(600000));
    },
    cancel() { cancelled = true; },
  }), { headers: { 'content-type': 'application/json' } }));
  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), { error: 'upstream_response_too_large' });
  assert.equal(cancelled, true);
});

test('normal Trakt API routes are not proxied by the auth Worker', async () => {
  for (const path of [
    '/v1/import/watched/movies', '/v1/import/watched/shows', '/v1/import/playback',
    '/v1/sync/history', '/v1/sync/history/remove', '/v1/sync/status', '/v1/sync/playback',
    '/v1/scrobble/start', '/v1/scrobble/pause', '/v1/scrobble/stop',
  ]) {
    let called = false;
    const response = await handle(new Request(`https://proxy.example${path}`, {
      method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}',
    }), env, async () => { called = true; return new Response('{}'); });
    assert.equal(response.status, 404, path);
    assert.equal(called, false, path);
  }
});

test('rejects unsupported routes and malformed device requests', async () => {
  const unsupported = await worker.fetch(new Request('https://proxy.example/nope'), env);
  assert.equal(unsupported.status, 404);
  const malformed = await worker.fetch(new Request('https://proxy.example/v1/device/token', { method: 'POST' }), env);
  assert.equal(malformed.status, 400);
});
