'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { runYtDlpSmart } = require('./ytdlp-smart');
const { getYouTubeLyrics } = require('./youtube-captions');
const util = require('util');
const { execFile } = require('child_process');
const execFileAsync = util.promisify(execFile);

const PORT = Number(process.env.DEEPECHO_BRIDGE_PORT || 17832);
const HOST = process.env.DEEPECHO_BRIDGE_HOST || '127.0.0.1';
const MAX_LIMIT = 30;
const streamCache = new Map();
const searchCache = new Map();
const STREAM_TTL = 7 * 60 * 1000;
const SEARCH_TTL = 4 * 60 * 1000;

function candidateExecutables() {
  const out = [];
  if (process.env.DEEPECHO_YTDLP) out.push(process.env.DEEPECHO_YTDLP);
  const localApp = process.env.LOCALAPPDATA || '';
  const programFiles = process.env.ProgramFiles || '';
  if (localApp) {
    out.push(path.join(localApp, 'Programs', 'DEEP-ECHO', 'resources', 'app', 'bin', 'yt-dlp.exe'));
    out.push(path.join(localApp, 'Programs', 'DEEP-ECHO', 'resources', 'app.asar.unpacked', 'bin', 'yt-dlp.exe'));
  }
  if (programFiles) out.push(path.join(programFiles, 'DEEP-ECHO', 'resources', 'app', 'bin', 'yt-dlp.exe'));
  out.push(path.join(__dirname, 'bin', process.platform === 'win32' ? 'yt-dlp.exe' : 'yt-dlp'));
  return out;
}

function findYtDlp() {
  for (const file of candidateExecutables()) if (file && fs.existsSync(file)) return file;
  const commands = process.platform === 'win32' ? ['yt-dlp.exe', 'yt-dlp'] : ['yt-dlp'];
  for (const cmd of commands) {
    try {
      execFileSync(cmd, ['--version'], { stdio:'ignore', windowsHide:true, timeout:3000 });
      return cmd;
    } catch {}
  }
  return '';
}

const YTDLP = findYtDlp();

function readBrowserAuth() {
  if (process.env.DEEPECHO_BROWSER) {
    const browser = String(process.env.DEEPECHO_BROWSER).toLowerCase();
    return { enabled:['chrome','edge','brave','firefox'].includes(browser), browser };
  }
  try {
    const appData = process.env.APPDATA || '';
    if (!appData) return { enabled:false, browser:'chrome' };
    const file = path.join(appData, 'deepeccho', 'youtube-access.json');
    const value = JSON.parse(fs.readFileSync(file, 'utf8'));
    const browser = ['chrome','edge','brave','firefox'].includes(value?.browser) ? value.browser : 'chrome';
    return { enabled:!!value?.enabled, browser };
  } catch { return { enabled:false, browser:'chrome' }; }
}


function json(res, status, body) {
  const payload = Buffer.from(JSON.stringify(body));
  res.writeHead(status, {
    'Content-Type':'application/json; charset=utf-8',
    'Content-Length':String(payload.length),
    'Cache-Control':'no-store',
    'Access-Control-Allow-Origin':'*'
  });
  res.end(payload);
}

function durationText(seconds) {
  const n = Math.max(0, Number(seconds) || 0);
  const m = Math.floor(n / 60);
  const s = Math.floor(n % 60);
  return `${m}:${String(s).padStart(2,'0')}`;
}

function mapEntry(e) {
  const id = String(e?.id || '').trim();
  if (!/^[A-Za-z0-9_-]{6,20}$/.test(id)) return null;
  return {
    id,
    title:String(e.title || 'Untitled'),
    artist:String(e.channel || e.uploader || ''),
    channel:String(e.channel || e.uploader || ''),
    duration:String(e.duration ? durationText(e.duration) : ''),
    durationMs:Math.round((Number(e.duration) || 0) * 1000),
    thumbnail:`https://i.ytimg.com/vi/${id}/hqdefault.jpg`,
    sourceUrl:`https://www.youtube.com/watch?v=${id}`,
    url:`https://www.youtube.com/watch?v=${id}`
  };
}

async function searchMusic(query, limit=20) {
  const q = String(query || '').replace(/\s+/g,' ').trim().slice(0,180);
  const n = Math.max(1, Math.min(MAX_LIMIT, Number(limit) || 20));
  if (!q) return [];
  if (!YTDLP) throw new Error('yt-dlp not found. Set DEEPECHO_YTDLP or put yt-dlp on PATH.');
  const key = `${q.toLowerCase()}::${n}`;
  const cached = searchCache.get(key);
  if (cached && cached.expiresAt > Date.now()) return cached.tracks;
  const { stdout } = await runYtDlpSmart(
    YTDLP,
    ['--flat-playlist','--no-warnings','-J',`ytsearch${n}:${q}`],
    { maxBuffer:20*1024*1024, timeout:45000, purpose:'android-search', browserAuth:readBrowserAuth(), allowFallbackClients:true }
  );
  const data = JSON.parse(stdout);
  const tracks = (data.entries || []).map(mapEntry).filter(Boolean);
  searchCache.set(key, { tracks, expiresAt:Date.now()+SEARCH_TTL });
  return tracks;
}

async function resolveStream(id, quality='auto') {
  const cleanId = String(id || '').trim();
  if (!/^[A-Za-z0-9_-]{6,20}$/.test(cleanId)) throw new Error('Invalid track id');
  if (!YTDLP) throw new Error('yt-dlp not found. Set DEEPECHO_YTDLP or put yt-dlp on PATH.');
  const q = ['high','medium','data'].includes(quality) ? quality : 'auto';
  const key = `${cleanId}:${q}`;
  const cached = streamCache.get(key);
  if (cached && cached.expiresAt > Date.now()) return cached.url;
  const format = q === 'data'
    ? 'bestaudio[ext=m4a][abr<=96]/bestaudio[abr<=96]/worstaudio/bestaudio'
    : q === 'medium'
      ? 'bestaudio[ext=m4a][abr<=160]/bestaudio[acodec^=mp4a][abr<=160]/bestaudio[abr<=160]/bestaudio'
      : 'bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio[ext=webm]/bestaudio/best';
  const url = `https://www.youtube.com/watch?v=${cleanId}`;
  const { stdout } = await runYtDlpSmart(
    YTDLP,
    ['--no-warnings','--no-playlist','-f',format,'-g',url],
    { maxBuffer:4*1024*1024, timeout:12000, purpose:'android-playback', browserAuth:readBrowserAuth(), allowFallbackClients:true, clientOrder:['default','web_embedded','android_vr'], ignoreGlobalBackoff:true, affectsGlobalBackoff:false }
  );
  const stream = String(stdout || '').split(/\r?\n/).map(v=>v.trim()).find(v=>/^https?:\/\//i.test(v));
  if (!stream) throw new Error('Playable audio URL not found');
  streamCache.set(key, { url:stream, expiresAt:Date.now()+STREAM_TTL });
  return stream;
}


function parseLrc(lrc='') {
  const out = [];
  for (const raw of String(lrc || '').split(/\r?\n/)) {
    const m = raw.match(/^\[(\d{1,3}):(\d{2}(?:\.\d{1,3})?)\](.*)$/);
    if (!m) continue;
    const text = String(m[3] || '').trim();
    if (!text) continue;
    out.push({ startMs:Math.round((Number(m[1]) * 60 + Number(m[2])) * 1000), text });
  }
  return out;
}

async function lyricsFor(id) {
  const cleanId = String(id || '').trim();
  if (!/^[A-Za-z0-9_-]{6,20}$/.test(cleanId)) throw new Error('Invalid track id');
  if (!YTDLP) throw new Error('yt-dlp not found.');
  const song = { id:cleanId, url:`https://www.youtube.com/watch?v=${cleanId}`, title:'', channel:'' };
  const result = await getYouTubeLyrics(song, YTDLP, execFileAsync, fetch, { browserAuth:readBrowserAuth(), tempRoot:path.join(__dirname,'.caption-cache') });
  const lines = parseLrc(result?.syncedLyrics || '');
  return { lines, source:String(result?.source || ''), language:String(result?.captionLanguage || '') };
}

let homeCache = { expiresAt:0, tracks:[] };
async function homeTracks() {
  if (homeCache.expiresAt > Date.now() && homeCache.tracks.length) return homeCache.tracks;
  const year = new Date().getFullYear();
  const queries = [
    `top hits ${year} songs official audio`,
    `new songs ${year} latest releases official audio`,
    `popular songs ${year} official audio`
  ];
  const groups = await Promise.all(queries.map(q => searchMusic(q, 8).catch(()=>[])));
  const seen = new Set();
  const tracks = [];
  for (const group of groups) for (const track of group) if (!seen.has(track.id)) { seen.add(track.id); tracks.push(track); }
  homeCache = { expiresAt:Date.now()+15*60*1000, tracks:tracks.slice(0,20) };
  return homeCache.tracks;
}

const server = http.createServer(async (req,res) => {
  if (req.method !== 'GET') return json(res,405,{success:false,error:'GET only'});
  let url;
  try { url = new URL(req.url, `http://${req.headers.host || 'localhost'}`); }
  catch { return json(res,400,{success:false,error:'Bad URL'}); }
  try {
    if (url.pathname === '/health') return json(res,200,{ok:!!YTDLP, ytDlp:!!YTDLP, version:'0.1.0'});
    if (url.pathname === '/search') {
      const tracks = await searchMusic(url.searchParams.get('q') || '', url.searchParams.get('limit') || 20);
      return json(res,200,{success:true,tracks});
    }
    if (url.pathname === '/home') {
      const tracks = await homeTracks();
      return json(res,200,{success:true,tracks});
    }
    if (url.pathname === '/resolve') {
      const streamUrl = await resolveStream(url.searchParams.get('id') || '', url.searchParams.get('quality') || 'auto');
      return json(res,200,{success:true,streamUrl});
    }
    if (url.pathname === '/lyrics') {
      const result = await lyricsFor(url.searchParams.get('id') || '');
      return json(res,200,{success:true,lyrics:result.lines,source:result.source,language:result.language});
    }
    return json(res,404,{success:false,error:'Not found'});
  } catch (error) {
    return json(res,500,{success:false,error:String(error?.message || error).replace(/\s+/g,' ').slice(0,600)});
  }
});

if (require.main === module) {
  server.listen(PORT, HOST, () => {
    console.log(`DeepEcho Android Bridge 0.1 listening on http://${HOST}:${PORT}`);
    console.log(YTDLP ? `yt-dlp: ${YTDLP}` : 'yt-dlp: NOT FOUND');
    if (HOST === '127.0.0.1') console.log('Android Emulator URL: http://10.0.2.2:17832');
  });
}

module.exports = { candidateExecutables, readBrowserAuth, mapEntry, durationText, parseLrc, searchMusic, resolveStream, lyricsFor, server };
