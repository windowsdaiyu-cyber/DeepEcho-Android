const { execFile, execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const util = require('util');
const { AsyncLocalStorage } = require('async_hooks');
const execFileAsync = util.promisify(execFile);

let anonymousBotBlockedUntil = 0;
let browserCookieBlockedUntil = 0;
const ytDlpRequestContext = new AsyncLocalStorage();

function runWithYtDlpContext(context, task) {
  return ytDlpRequestContext.run(context || {}, task);
}

function errorText(error) {
  return [error?.message, error?.stderr, error?.stdout].filter(Boolean).join('\n');
}

function cleanErrorMessage(error) {
  return errorText(error)
    .replace(/∩┐╜/g, "'")
    .replace(/\uFFFD/g, "'")
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 900);
}

function isBotChallenge(error) {
  const text = errorText(error).toLowerCase();
  return /sign in to confirm|not a bot|captcha|confirm you.?re not a bot|login required|authentication required/.test(text);
}

function isAccess403(error) {
  const text = errorText(error).toLowerCase();
  return /http error 403|forbidden|po token|requested format is not available/.test(text);
}

function isAbort(error) {
  return error?.name === 'AbortError' || /aborted|operation was aborted/i.test(error?.message || '');
}

function isBrowserCookieError(error) {
  const text = errorText(error).toLowerCase();
  return /cookies?-from-browser|cookie database|could not copy .*cookie|unable to open database|permission denied|failed to decrypt|dpapi|app[- ]bound|browser.*not found|profile.*not found/.test(text);
}

function runtimeArgs(executable, args = []) {
  if (args.includes('--js-runtimes')) return [];
  const binDir = path.dirname(executable);
  const localDeno = path.join(binDir, process.platform === 'win32' ? 'deno.exe' : 'deno');
  const out = [];
  if (fs.existsSync(localDeno)) {
    out.push('--js-runtimes', `deno:${localDeno}`);
  } else {
    // yt-dlp only supports Node 22+ for the modern YouTube challenge solver.
    // Do not force an older system Node and accidentally disable yt-dlp's default Deno path.
    try {
      const raw = String(execFileSync(process.platform === 'win32' ? 'node.exe' : 'node', ['--version'], { windowsHide:true, encoding:'utf8', timeout:2500 }) || '').trim();
      const major = Number((raw.match(/v?(\d+)/) || [])[1] || 0);
      if (major >= 22) out.push('--js-runtimes', 'node');
    } catch {}
  }
  // Official yt-dlp executables normally bundle the EJS solver, but allowing the
  // official GitHub component gives recovery if a nightly build needs a newer solver.
  if (!args.includes('--remote-components')) out.push('--remote-components', 'ejs:github');
  return out;
}

function clientAttemptArgs(kind) {
  if (kind === 'android_vr') return ['--extractor-args', 'youtube:player_client=android_vr'];
  if (kind === 'android') return ['--extractor-args', 'youtube:player_client=android'];
  if (kind === 'web_embedded') return ['--extractor-args', 'youtube:player_client=web_embedded'];
  if (kind === 'web_safari') return ['--extractor-args', 'youtube:player_client=web_safari'];
  if (kind === 'embedded') return ['--extractor-args', 'youtube:player_client=default,web_embedded,-android_vr'];
  return [];
}

async function runYtDlpSmart(executable, args, options = {}) {
  const {
    maxBuffer = 20 * 1024 * 1024,
    timeout = 45000,
    signal,
    purpose = 'generic',
    browserAuth = null,
    allowFallbackClients = true,
    affectsGlobalBackoff,
    clientOrder = null,
    ignoreGlobalBackoff = false
  } = options;
  if (signal?.aborted) throw Object.assign(new Error('Operation aborted'), { name: 'AbortError' });
  const requestContext = ytDlpRequestContext.getStore() || {};
  const shouldAffectGlobalBackoff = affectsGlobalBackoff === undefined
    ? requestContext.affectsGlobalBackoff !== false
    : affectsGlobalBackoff !== false;

  const attempts = [];
  const anonymousCooldown = !ignoreGlobalBackoff && Date.now() < anonymousBotBlockedUntil;
  if (Array.isArray(clientOrder) && clientOrder.length) {
    const seen = new Set();
    for (const rawKind of clientOrder) {
      const kind = String(rawKind || '').trim();
      if (!kind || seen.has(kind)) continue;
      seen.add(kind);
      if (kind === 'default' && anonymousCooldown) continue;
      attempts.push({ name: kind, prefix: clientAttemptArgs(kind) });
    }
  } else {
    if (!anonymousCooldown) attempts.push({ name: 'default', prefix: [] });
    if (allowFallbackClients) {
      attempts.push({ name: 'android', prefix: clientAttemptArgs('android') });
      attempts.push({ name: 'embedded', prefix: clientAttemptArgs('embedded') });
    }
  }
  if (!attempts.length) attempts.push({ name: 'android', prefix: clientAttemptArgs('android') });
  if (browserAuth?.enabled && browserAuth.browser && Date.now() >= browserCookieBlockedUntil) {
    attempts.push({ name: `browser:${browserAuth.browser}`, prefix: ['--cookies-from-browser', browserAuth.browser] });
  }

  let lastError = null;
  let botSeen = false;
  for (let i = 0; i < attempts.length; i++) {
    const attempt = attempts[i];
    if (signal?.aborted) throw Object.assign(new Error('Operation aborted'), { name: 'AbortError' });
    try {
      // Prefer DeepEcho's private Deno runtime when Setup-YtDlp installed it.
      // V29 accidentally forced system Node and therefore ignored that Deno copy.
      const jsArgs = runtimeArgs(executable, args);
      const result = await execFileAsync(executable, [...attempt.prefix, ...jsArgs, ...args], {
        maxBuffer,
        windowsHide: true,
        timeout,
        signal,
        env: { ...process.env, PYTHONUTF8: '1', PYTHONIOENCODING: 'utf-8' }
      });
      if (attempt.name !== 'default' && purpose !== 'prefetch') {
        console.log(`[yt-dlp] ${purpose} recovered via ${attempt.name}`);
      }
      return { ...result, attempt: attempt.name };
    } catch (error) {
      if (isAbort(error)) throw error;
      lastError = error;
      botSeen = botSeen || isBotChallenge(error);
      if (attempt.name.startsWith('browser:') && isBrowserCookieError(error)) {
        // Chromium browsers on Windows commonly lock or app-bind their cookie DB.
        // Do not hammer the same broken browser-cookie path on every Play click.
        browserCookieBlockedUntil = Date.now() + 5 * 60 * 1000;
      }
      const shouldTryNext = i < attempts.length - 1 && (isBotChallenge(error) || isAccess403(error) || isBrowserCookieError(error));
      if (!shouldTryNext) break;
    }
  }

  if (botSeen && shouldAffectGlobalBackoff) anonymousBotBlockedUntil = Date.now() + 2 * 60 * 1000;
  const wrapped = new Error(cleanErrorMessage(lastError) || 'YouTube request failed.');
  wrapped.code = botSeen ? 'YOUTUBE_BOT_CHECK' : (isAccess403(lastError) ? 'YOUTUBE_ACCESS_403' : (lastError?.code || 'YTDLP_FAILED'));
  wrapped.botChallenge = botSeen;
  wrapped.original = lastError;
  throw wrapped;
}

function resetYouTubeBackoff() {
  anonymousBotBlockedUntil = 0;
  browserCookieBlockedUntil = 0;
}

function getYouTubeBackoffState() {
  return { anonymousBotBlockedUntil, browserCookieBlockedUntil };
}

module.exports = {
  runYtDlpSmart,
  runWithYtDlpContext,
  isBotChallenge,
  isAccess403,
  isAbort,
  isBrowserCookieError,
  runtimeArgs,
  cleanErrorMessage,
  resetYouTubeBackoff,
  getYouTubeBackoffState
};
