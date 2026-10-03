const fs = require('fs');
const os = require('os');
const path = require('path');
const { normalizeTimeline } = require('./lyrics-timeline');
const { runYtDlpSmart, isAbort } = require('./ytdlp-smart');
function decodeHtml(text = '') {
  return String(text)
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&nbsp;/g, ' ');
}

function cleanCaptionText(text = '') {
  return decodeHtml(String(text))
    .replace(/<[^>]+>/g, '')
    .replace(/\[[^\]]*(music|applause|instrumental)[^\]]*\]/gi, '')
    .replace(/\([^)]*(music|applause|instrumental)[^)]*\)/gi, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function toLrcTime(seconds) {
  const safe = Math.max(0, Number(seconds) || 0);
  const minutes = Math.floor(safe / 60);
  const secs = safe - minutes * 60;
  return `[${String(minutes).padStart(2, '0')}:${secs.toFixed(2).padStart(5, '0')}]`;
}

function captionKey(value = '') {
  return String(value).normalize('NFKC').toLocaleLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}
function captionTokenOverlap(a, b) {
  const A = new Set(captionKey(a).split(/\s+/).filter(Boolean));
  const B = new Set(captionKey(b).split(/\s+/).filter(Boolean));
  if (!A.size || !B.size) return 0;
  let hit = 0; for (const token of A) if (B.has(token)) hit++;
  return hit / Math.max(A.size, B.size);
}
function collapseCaptionLines(lines, options = {}) {
  const rolling = options.rolling !== false;
  const output = [];
  for (const line of [...lines].sort((a, b) => a.time - b.time)) {
    const text = cleanCaptionText(line.text);
    if (!text) continue;
    const key = captionKey(text);
    const prev = output[output.length - 1];
    if (prev) {
      const prevKey = captionKey(prev.text);
      const dt = Number(line.time) - Number(prev.time);
      // IMPORTANT: repeated lyric lines are legitimate. Old code removed every adjacent
      // identical caption regardless of time, which made choruses/repeated English lines
      // disappear and the highlighter appear stuck. Only collapse near-simultaneous emits.
      if (key === prevKey && dt >= 0 && dt <= 0.72) continue;
      // Auto captions often grow one caption word-by-word. Preserve the first timestamp.
      const progressive = rolling && dt >= 0 && dt <= 1.35 && (key.startsWith(prevKey + ' ') || prevKey.startsWith(key + ' '));
      if (progressive) {
        if (key.length >= prevKey.length) prev.text = text;
        continue;
      }
      // Rolling caption windows may differ at both ends rather than being a pure prefix.
      // Merge only very-near, high-overlap windows; real repeated chorus lines farther apart survive.
      if (rolling && dt >= 0 && dt <= 0.85 && captionTokenOverlap(prev.text, text) >= 0.84) {
        if (key.length > prevKey.length) prev.text = text;
        continue;
      }
    }
    output.push({ time: Math.max(0, Number(line.time) || 0), text });
  }
  return output;
}

function timedWordsFromText(text, start, end) {
  const tokens = cleanCaptionText(text).split(/\s+/).filter(Boolean);
  if (!tokens.length) return [];
  const safeStart = Math.max(0, Number(start) || 0);
  const safeEnd = Math.max(safeStart + 0.08, Number(end) || safeStart + Math.max(0.28, tokens.length * 0.22));
  const totalWeight = tokens.reduce((sum, token) => sum + Math.max(1, token.replace(/[^\p{L}\p{N}]/gu, '').length), 0) || tokens.length;
  let cursor = safeStart;
  return tokens.map((text, index) => {
    const weight = Math.max(1, text.replace(/[^\p{L}\p{N}]/gu, '').length);
    const duration = (safeEnd - safeStart) * (weight / totalWeight);
    const row = { text, time: cursor, end: index === tokens.length - 1 ? safeEnd : cursor + duration };
    cursor = row.end;
    return row;
  });
}
function normalizeWordTimings(items = []) {
  const output = [];
  for (const item of [...items].sort((a,b)=>(Number(a.time)||0)-(Number(b.time)||0))) {
    const text = cleanCaptionText(item?.text || '');
    const time = Math.max(0, Number(item?.time) || 0);
    const end = Math.max(time + 0.04, Number(item?.end) || time + 0.28);
    if (!text) continue;
    const prev = output[output.length - 1];
    if (prev && captionKey(prev.text) === captionKey(text) && Math.abs(prev.time - time) < 0.045) continue;
    output.push({ text, time: Math.round(time * 1000) / 1000, end: Math.round(end * 1000) / 1000 });
    if (output.length >= 20000) break;
  }
  return output;
}
function parseJson3Detailed(data, options = {}) {
  const rows = [], wordTimings = [];
  for (const event of data?.events || []) {
    const segs = Array.isArray(event.segs) ? event.segs : [];
    const text = segs.map(seg => seg.utf8 || '').join('');
    if (!text.trim()) continue;
    const eventStart = (Number(event.tStartMs) || 0) / 1000;
    const eventDuration = Math.max(0.18, (Number(event.dDurationMs) || 0) / 1000 || 4);
    rows.push({ time: eventStart, text });
    for (let i = 0; i < segs.length; i++) {
      const seg = segs[i]; const raw = String(seg?.utf8 || ''); if (!raw.trim()) continue;
      const segStart = eventStart + Math.max(0, Number(seg?.tOffsetMs) || 0) / 1000;
      const nextOffset = Number(segs[i + 1]?.tOffsetMs);
      const segEnd = Number.isFinite(nextOffset) ? eventStart + Math.max(Number(seg?.tOffsetMs) || 0, nextOffset) / 1000 : eventStart + eventDuration;
      wordTimings.push(...timedWordsFromText(raw, segStart, segEnd));
    }
  }
  return { rows: collapseCaptionLines(rows, options), wordTimings: normalizeWordTimings(wordTimings) };
}
function parseJson3(data, options = {}) { return parseJson3Detailed(data, options).rows; }

function parseVttDetailed(vtt = '', options = {}) {
  const rows = [], wordTimings = [];
  const blocks = String(vtt).replace(/\r/g, '').split(/\n\n+/);
  const clock = value => { const parts = String(value || '').trim().replace(',', '.').split(':').map(Number); return parts.length >= 2 && parts.every(Number.isFinite) ? parts.reduce((total, part) => total * 60 + part, 0) : NaN; };
  for (const block of blocks) {
    const lines = block.split('\n').map(v => v.trim()).filter(Boolean);
    const timingIndex = lines.findIndex(v => /-->/.test(v)); if (timingIndex < 0) continue;
    const cue = lines[timingIndex].split('-->'); const seconds = clock(cue[0]); const cueEnd = clock((cue[1] || '').split(/\s+/)[0]); if (!Number.isFinite(seconds)) continue;
    const rawText = lines.slice(timingIndex + 1).join(' '); rows.push({ time: seconds, text: rawText });
    const stamp = /<(\d{1,2}:\d{2}:\d{2}[.,]\d+)>/g; const matches = [...rawText.matchAll(stamp)];
    if (matches.length) {
      const firstText = rawText.slice(0, matches[0].index); if (cleanCaptionText(firstText)) wordTimings.push(...timedWordsFromText(firstText, seconds, clock(matches[0][1])));
      for (let i = 0; i < matches.length; i++) {
        const start = clock(matches[i][1]); const end = i + 1 < matches.length ? clock(matches[i + 1][1]) : cueEnd;
        const from = matches[i].index + matches[i][0].length; const to = i + 1 < matches.length ? matches[i + 1].index : rawText.length;
        wordTimings.push(...timedWordsFromText(rawText.slice(from, to), start, end));
      }
    }
  }
  return { rows: collapseCaptionLines(rows, options), wordTimings: normalizeWordTimings(wordTimings) };
}
function parseVtt(vtt = '', options = {}) { return parseVttDetailed(vtt, options).rows; }

function rowsToLrc(rows) {
  return (rows || []).map(row => `${toLrcTime(row.time)}${row.text}`).join('\n');
}

function normalizeLang(value = '') {
  return String(value || '').trim().toLowerCase().replace('_', '-');
}

function captionLanguageScore(lang, formats, info) {
  const key = normalizeLang(lang);
  const base = key.split('-')[0];
  const original = normalizeLang(info?.language || info?.original_language || '');
  const originalBase = original.split('-')[0];
  const names = (formats || []).map(x => String(x?.name || '')).join(' ').toLowerCase();
  let score = 0;
  if (original && (key === original || base === originalBase)) score += 120;
  if (/-orig$/.test(key) || /original/.test(names)) score += 55;
  if (base === 'en') score += 24;
  if (base === 'hi') score += 18;
  if (/translated|translation/.test(names)) score -= 70;
  if (/auto-translated|translated/.test(key)) score -= 70;
  return score;
}

function chooseCaptionTrack(info) {
  const sources = [
    { kind: 'subtitles', tracks: info?.subtitles || {}, sourceScore: 30 },
    { kind: 'automatic captions', tracks: info?.automatic_captions || {}, sourceScore: 0 }
  ];
  const candidates = [];
  for (const source of sources) {
    for (const [lang, formats] of Object.entries(source.tracks || {})) {
      const base = normalizeLang(lang).split('-')[0];
      const originalBase = normalizeLang(info?.language || info?.original_language || '').split('-')[0];
      // Never silently substitute an English/Hindi auto-translation for a song's
      // source language. If YouTube tells us the original language, only that language
      // (including its -orig variant) is eligible. Romanization happens later in our UI.
      const names = (formats || []).map(x => String(x?.name || '')).join(' ').toLowerCase();
      const translated = /translated|translation|auto-translated/.test(`${normalizeLang(lang)} ${names}`);
      if (translated) continue;
      if (originalBase) {
        if (base !== originalBase) continue;
      } else {
        // When metadata does not expose the source language, keep non-translated
        // language tracks eligible and strongly prefer YouTube's explicit -orig/original
        // variant. This prevents a Punjabi/Arabic song from silently falling to English.
        const explicitlyOriginal = /-orig$/.test(normalizeLang(lang)) || /original/.test(names);
        if (!explicitlyOriginal && !base) continue;
      }
      const track = (formats || []).find(x => x.ext === 'json3') || (formats || []).find(x => x.ext === 'vtt') || (formats || [])[0];
      if (!track?.url) continue;
      const explicitOriginalBonus = (/-orig$/.test(normalizeLang(lang)) || /original/.test(names)) ? 140 : 0;
      candidates.push({ ...track, lang, kind: source.kind, score: source.sourceScore + captionLanguageScore(lang, formats, info) + explicitOriginalBonus });
    }
  }
  candidates.sort((a, b) => b.score - a.score);
  return candidates[0] || null;
}

function parseLrcRows(value = '') {
  const rows = [];
  for (const line of String(value).split(/\r?\n/)) {
    const text = line.replace(/\[\d+:\d+(?:\.\d+)?\]/g, '').trim();
    for (const match of line.matchAll(/\[(\d+):(\d+(?:\.\d+)?)\]/g)) {
      rows.push({ time: Number(match[1]) * 60 + Number(match[2]), text });
    }
  }
  return rows.filter(row => row.text).sort((a, b) => a.time - b.time);
}

function syncTokens(value = '') {
  return String(value).normalize('NFKC').toLocaleLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ').trim().split(/\s+/).filter(Boolean);
}

function syncLineScore(a, b) {
  const at = syncTokens(a), bt = syncTokens(b);
  if (!at.length || !bt.length) return 0;
  const as = at.join(' '), bs = bt.join(' ');
  if (as === bs) return 1;
  if ((as.includes(bs) || bs.includes(as)) && Math.min(at.length, bt.length) >= 2) {
    return 0.76 + 0.18 * (Math.min(at.length, bt.length) / Math.max(at.length, bt.length));
  }
  const left = new Set(at), right = new Set(bt);
  let common = 0;
  for (const token of left) if (right.has(token)) common++;
  const union = new Set([...left, ...right]).size || 1;
  const jaccard = common / union;
  const coverage = common / Math.max(1, Math.min(left.size, right.size));
  return jaccard * 0.55 + coverage * 0.45;
}

function median(values) {
  const sorted = values.filter(Number.isFinite).sort((a, b) => a - b);
  if (!sorted.length) return 0;
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

function alignSyncedLyricsToCaptions(lyricsLrc, captionLrc) {
  const lyrics = parseLrcRows(lyricsLrc), captions = parseLrcRows(captionLrc);
  if (lyrics.length < 2 || captions.length < 2) return null;

  // v2.3.0 V9: build several text anchors and warp the provider timeline through those
  // anchors instead of applying one global offset. This matters for official music videos
  // that contain an intro, dialogue break, outro, or a slightly different master edit.
  // Provider text stays authoritative; captions are used as timing evidence only.
  const matches = [];
  let nextCaption = 0;
  let previous = null;
  for (let i = 0; i < lyrics.length; i++) {
    const from = Math.max(0, nextCaption);
    const to = previous ? Math.min(captions.length, from + 42) : captions.length;
    let best = null;
    const expectedTime = previous
      ? captions[previous.captionIndex].time + Math.max(0, lyrics[i].time - lyrics[previous.lyricIndex].time)
      : null;

    for (let j = from; j < to; j++) {
      // A YouTube caption cue can split one lyric line across two/three caption events.
      // Matching short spans substantially improves anchors on automatic captions.
      for (let span = 1; span <= Math.min(3, to - j); span++) {
        const joined = captions.slice(j, j + span).map(row => row.text).join(' ');
        const textScore = syncLineScore(lyrics[i].text, joined);
        const distancePenalty = expectedTime == null ? 0 : Math.min(0.22, Math.abs(captions[j].time - expectedTime) / 40);
        const spanPenalty = (span - 1) * 0.012;
        const score = textScore - distancePenalty - spanPenalty;
        if (!best || score > best.score + 1e-6 || (Math.abs(score - best.score) <= 1e-6 && j < best.captionIndex)) {
          best = { lyricIndex:i, captionIndex:j, span, score, textScore };
        }
      }
    }

    if (!best) continue;
    const minTokens = Math.min(syncTokens(lyrics[i].text).length,
      syncTokens(captions.slice(best.captionIndex, best.captionIndex + best.span).map(x => x.text).join(' ')).length);
    const threshold = minTokens <= 1 ? 0.98 : minTokens === 2 ? 0.64 : 0.48;
    if (best.textScore >= threshold && best.score >= threshold - 0.18) {
      matches.push(best);
      previous = best;
      nextCaption = best.captionIndex + best.span;
    }
  }
  if (matches.length < 2) return null;

  const anchors = matches.map(m => ({
    lyricIndex:m.lyricIndex,
    captionIndex:m.captionIndex,
    lyricTime:lyrics[m.lyricIndex].time,
    captionTime:captions[m.captionIndex].time,
    score:m.textScore
  })).sort((a,b) => a.lyricIndex - b.lyricIndex);

  const offsets = anchors.map(a => a.captionTime - a.lyricTime);
  const globalOffset = median(offsets);
  const matchedTimes = new Map(anchors.map(a => [a.lyricIndex, a.captionTime]));

  function mappedTimeFor(index) {
    if (matchedTimes.has(index)) return matchedTimes.get(index);
    const sourceTime = lyrics[index].time;
    let left = null, right = null;
    for (const anchor of anchors) {
      if (anchor.lyricIndex < index) left = anchor;
      else if (anchor.lyricIndex > index) { right = anchor; break; }
    }
    if (left && right && right.lyricTime > left.lyricTime + 0.05) {
      // Interpolate the OFFSET, not just a single fixed shift. If the video inserts a
      // dialogue scene, the offset gradually moves between the surrounding real anchors.
      const ratio = Math.max(0, Math.min(1, (sourceTime - left.lyricTime) / (right.lyricTime - left.lyricTime)));
      const leftOffset = left.captionTime - left.lyricTime;
      const rightOffset = right.captionTime - right.lyricTime;
      return sourceTime + leftOffset + (rightOffset - leftOffset) * ratio;
    }
    if (left) return sourceTime + (left.captionTime - left.lyricTime);
    if (right) return sourceTime + (right.captionTime - right.lyricTime);
    return sourceTime + globalOffset;
  }

  const out = lyrics.map((row, i) => ({ time:Math.max(0, mappedTimeFor(i)), text:row.text }));
  const normalizedOut = normalizeTimeline(out, { dedupeWindow:0.18, groupWindow:0.24 });
  const avgTextScore = matches.reduce((sum,m)=>sum + Math.max(0,m.textScore),0) / matches.length;
  const anchorRatio = Math.min(1, matches.length / Math.max(2, Math.min(8, lyrics.length)));
  const spread = lyrics.length > 1 ? Math.max(0, (anchors[anchors.length-1].lyricIndex - anchors[0].lyricIndex) / (lyrics.length - 1)) : 1;
  const confidence = avgTextScore * (0.58 + 0.27 * anchorRatio + 0.15 * spread);
  const firstOffset = offsets[0] || 0, lastOffset = offsets[offsets.length - 1] || 0;
  return {
    syncedLyrics: rowsToLrc(normalizedOut),
    matched: matches.length,
    confidence: Math.round(Math.max(0, Math.min(1, confidence)) * 1000) / 1000,
    offset: Math.round(globalOffset * 1000) / 1000,
    timingAnchors: anchors.length,
    timingDriftSeconds: Math.round((lastOffset - firstOffset) * 1000) / 1000,
    timingWarp: anchors.length >= 3 ? 'piecewise-anchor-warp' : 'anchor-offset'
  };
}


function alignSyncedLyricsToCaptionsStable(lyricsLrc, captionLrc) {
  const lyrics = parseLrcRows(lyricsLrc), captions = parseLrcRows(captionLrc);
  if (lyrics.length < 2 || captions.length < 2) return null;

  // V23: context-aware monotonic sequence alignment. The old greedy matcher could commit
  // to a locally-good repeated chorus occurrence and make the remaining song run early/late.
  // Build all credible lyric↔caption candidates first, then choose one coherent path across
  // the whole recording. Unique surrounding lines naturally disambiguate repeated choruses.
  const lyricKeys = lyrics.map(row => syncTokens(row.text).join(' '));
  const keyFreq = new Map();
  for (const key of lyricKeys) if (key) keyFreq.set(key, (keyFreq.get(key) || 0) + 1);
  const candidates = [];
  for (let i = 0; i < lyrics.length; i++) {
    for (let j = 0; j < captions.length; j++) {
      for (let span = 1; span <= Math.min(3, captions.length - j); span++) {
        const joined = captions.slice(j, j + span).map(row => row.text).join(' ');
        const textScore = syncLineScore(lyrics[i].text, joined);
        const minTokens = Math.min(syncTokens(lyrics[i].text).length, syncTokens(joined).length);
        const threshold = minTokens <= 1 ? 0.98 : minTokens === 2 ? 0.64 : 0.48;
        if (textScore < threshold) continue;
        const unique = (keyFreq.get(lyricKeys[i]) || 0) <= 1;
        const tokenEvidence = Math.min(0.16, Math.max(0, minTokens - 2) * 0.025);
        const evidence = textScore * 2.35 + (unique ? 0.20 : -0.035) + tokenEvidence - (span - 1) * 0.018;
        candidates.push({
          lyricIndex:i, captionIndex:j, captionEnd:j + span, span, textScore, evidence, unique,
          lyricTime:lyrics[i].time, captionTime:captions[j].time,
          offset:captions[j].time - lyrics[i].time
        });
      }
    }
  }
  if (candidates.length < 2) return null;
  candidates.sort((a,b) => a.lyricIndex - b.lyricIndex || a.captionIndex - b.captionIndex || b.textScore - a.textScore);

  const dp = candidates.map(node => ({
    score:node.evidence - Math.min(.52, Math.abs(node.offset) * .006),
    count:1, uniqueCount:node.unique ? 1 : 0, prev:-1,
    firstCaption:node.captionIndex
  }));
  for (let i = 0; i < candidates.length; i++) {
    const b = candidates[i];
    for (let k = 0; k < i; k++) {
      const a = candidates[k];
      if (b.lyricIndex <= a.lyricIndex || b.captionIndex < a.captionEnd) continue;
      const sourceDelta = b.lyricTime - a.lyricTime;
      const targetDelta = b.captionTime - a.captionTime;
      if (sourceDelta <= .05 || targetDelta <= .05) continue;
      const ratio = targetDelta / sourceDelta;
      // Allow cinematic insertions and small edit differences, but reject impossible jumps
      // that usually mean a chorus was paired with the wrong occurrence.
      if (ratio < .30 || ratio > 3.10) continue;
      const lyricGap = b.lyricIndex - a.lyricIndex;
      const offsetJump = Math.abs(b.offset - a.offset);
      const insertedGap = Math.abs(targetDelta - sourceDelta) >= 1.7 && offsetJump >= 1.7;
      let continuity = lyricGap === 1 ? .34 : Math.max(-.12, .16 - (lyricGap - 1) * .018);
      continuity -= Math.min(.58, Math.abs(targetDelta - sourceDelta) * (insertedGap ? .007 : .018));
      continuity -= Math.min(.24, offsetJump * (insertedGap ? .004 : .012));
      const score = dp[k].score + b.evidence + continuity;
      const count = dp[k].count + 1;
      const uniqueCount = dp[k].uniqueCount + (b.unique ? 1 : 0);
      const better = count > dp[i].count ||
        (count === dp[i].count && uniqueCount > dp[i].uniqueCount) ||
        (count === dp[i].count && uniqueCount === dp[i].uniqueCount && score > dp[i].score + 1e-6) ||
        (count === dp[i].count && uniqueCount === dp[i].uniqueCount && Math.abs(score - dp[i].score) <= 1e-6 && dp[k].firstCaption < dp[i].firstCaption);
      if (better) dp[i] = { score, count, uniqueCount, prev:k, firstCaption:dp[k].firstCaption };
    }
  }
  let best = -1;
  for (let i = 0; i < dp.length; i++) {
    if (best < 0 || dp[i].count > dp[best].count ||
        (dp[i].count === dp[best].count && dp[i].uniqueCount > dp[best].uniqueCount) ||
        (dp[i].count === dp[best].count && dp[i].uniqueCount === dp[best].uniqueCount && dp[i].score > dp[best].score)) best = i;
  }
  if (best < 0 || dp[best].count < 2) return null;
  const matches = [];
  for (let i = best; i >= 0; i = dp[i].prev) {
    matches.push(candidates[i]);
    if (dp[i].prev < 0) break;
  }
  matches.reverse();

  const anchors = matches.map(m => ({
    lyricIndex:m.lyricIndex, captionIndex:m.captionIndex,
    lyricTime:m.lyricTime, captionTime:m.captionTime, score:m.textScore,
    unique:m.unique, kind:'caption-text'
  }));
  const offsets = anchors.map(a => a.captionTime - a.lyricTime);
  const globalOffset = median(offsets);
  const matchedTimes = new Map(anchors.map(a => [a.lyricIndex, a.captionTime]));

  function mappedTimeFor(index) {
    if (matchedTimes.has(index)) return matchedTimes.get(index);
    const sourceTime = lyrics[index].time;
    let left = null, right = null;
    for (const anchor of anchors) {
      if (anchor.lyricIndex < index) left = anchor;
      else if (anchor.lyricIndex > index) { right = anchor; break; }
    }
    if (left && right && right.lyricTime > left.lyricTime + .05) {
      const leftOffset = left.captionTime - left.lyricTime;
      const rightOffset = right.captionTime - right.lyricTime;
      const sourceSpan = right.lyricTime - left.lyricTime;
      const targetSpan = right.captionTime - left.captionTime;
      const offsetJump = rightOffset - leftOffset;
      // Reuse the stable-section principle already proven by V18's lyrical-reference path:
      // a real inserted dialogue/scene gap is a discontinuity, not gradual tempo drift.
      // Unmatched lines stay with the nearest section instead of drifting through the gap.
      if (Math.abs(offsetJump) >= 1.8 && Math.abs(targetSpan - sourceSpan) >= 1.6 && sourceSpan <= 80) {
        const cut = (left.lyricTime + right.lyricTime) / 2;
        return sourceTime + (sourceTime < cut ? leftOffset : rightOffset);
      }
      const ratio = Math.max(0, Math.min(1, (sourceTime - left.lyricTime) / sourceSpan));
      return sourceTime + leftOffset + (rightOffset - leftOffset) * ratio;
    }
    if (left) return sourceTime + (left.captionTime - left.lyricTime);
    if (right) return sourceTime + (right.captionTime - right.lyricTime);
    return sourceTime + globalOffset;
  }

  const out = [];
  let previousTime = -1;
  for (let i = 0; i < lyrics.length; i++) {
    let time = Math.max(0, mappedTimeFor(i));
    if (time <= previousTime) time = previousTime + .08;
    previousTime = time;
    out.push({ time, text:lyrics[i].text });
  }
  const normalizedOut = normalizeTimeline(out, { dedupeWindow:0.18, groupWindow:0.24 });
  const avgTextScore = matches.reduce((sum,m)=>sum + Math.max(0,m.textScore),0) / matches.length;
  const anchorRatio = Math.min(1, matches.length / Math.max(2, Math.min(8, lyrics.length)));
  const spread = lyrics.length > 1 ? Math.max(0, (anchors[anchors.length-1].lyricIndex - anchors[0].lyricIndex) / (lyrics.length - 1)) : 1;
  const uniqueMatched = anchors.filter(a => a.unique).length;
  const uniqueRatio = Math.min(1, uniqueMatched / Math.max(2, Math.min(6, new Set(lyricKeys.filter(Boolean)).size || 2)));
  const confidence = avgTextScore * (0.50 + 0.22 * anchorRatio + 0.14 * spread + 0.14 * uniqueRatio);
  const firstOffset = offsets[0] || 0, lastOffset = offsets[offsets.length - 1] || 0;
  return {
    syncedLyrics: rowsToLrc(normalizedOut),
    matched: matches.length,
    uniqueMatched,
    confidence: Math.round(Math.max(0, Math.min(1, confidence)) * 1000) / 1000,
    offset: Math.round(globalOffset * 1000) / 1000,
    timingAnchors: anchors.length,
    anchorSpread:Math.round(spread * 1000) / 1000,
    timingDriftSeconds: Math.round((lastOffset - firstOffset) * 1000) / 1000,
    timingWarp: anchors.length >= 3 ? 'context-sequence-warp' : 'context-anchor-offset'
  };
}

function subtitleLanguageCandidates(metadata = {}, track = null) {
  const raw = [track?.lang, metadata.originalLanguage, metadata.language, 'en', 'hi']
    .map(normalizeLang).filter(Boolean);
  const seen = new Set(), out = [];
  for (const lang of raw) {
    const base = lang.split('-')[0];
    for (const value of [lang, base]) {
      if (!value || seen.has(value)) continue;
      seen.add(value); out.push(value);
    }
  }
  // yt-dlp accepts regex-like language selectors. Keep this intentionally narrow so
  // a missing original-language subtitle cannot download dozens of auto translations.
  return out.slice(0, 5).flatMap(lang => [`${lang}.*`, lang]).join(',');
}

function parseCaptionFile(filePath, options = {}) {
  const ext = path.extname(filePath).toLowerCase();
  const raw = fs.readFileSync(filePath, 'utf8');
  if (ext === '.json3') return parseJson3Detailed(JSON.parse(raw), options);
  if (ext === '.vtt') return parseVttDetailed(raw, options);
  return { rows: [], wordTimings: [] };
}

function captionFileScore(name = '', metadata = {}, track = null) {
  const lower = String(name).toLowerCase();
  let score = lower.endsWith('.json3') ? 15 : lower.endsWith('.vtt') ? 8 : 0;
  const preferred = [track?.lang, metadata.originalLanguage, metadata.language, 'en', 'hi']
    .map(normalizeLang).filter(Boolean);
  for (let i = 0; i < preferred.length; i++) {
    const lang = preferred[i], base = lang.split('-')[0];
    if (lower.includes(`.${lang}.`) || lower.includes(`.${base}.`)) score += 50 - i * 6;
  }
  return score;
}

async function materializeCaptionFallback(song, ytdlpPath, metadata, track, options = {}) {
  if (!song?.url || !ytdlpPath) return null;
  const base = options.tempRoot || path.join(os.tmpdir(), 'deepecho-lyrics-captions');
  const dir = path.join(base, `captions-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`);
  fs.mkdirSync(dir, { recursive: true });
  try {
    const langs = subtitleLanguageCandidates(metadata, track);
    const args = [
      '--no-warnings', '--no-playlist', '--skip-download',
      '--write-subs', '--write-auto-subs',
      '--sub-format', 'json3/vtt/best',
      '--sub-langs', langs || 'en.*,en,hi.*,hi',
      '-o', path.join(dir, 'caption.%(ext)s'),
      song.url
    ];
    await runYtDlpSmart(ytdlpPath, args, {
      maxBuffer: 20 * 1024 * 1024,
      timeout: 45000,
      signal: options.signal,
      purpose: 'lyrics-captions',
      browserAuth: options.browserAuth,
      allowFallbackClients: true
    });
    const files = fs.readdirSync(dir)
      .filter(name => /\.(?:json3|vtt)$/i.test(name))
      .sort((a,b) => captionFileScore(b, metadata, track) - captionFileScore(a, metadata, track));
    for (const name of files) {
      try {
        const automatic = track ? track.kind === 'automatic captions' : true;
        const parsed = parseCaptionFile(path.join(dir, name), { rolling: automatic });
        if (parsed.rows?.length) {
          return {
            rows: parsed.rows,
            wordTimings: parsed.wordTimings || [],
            source: track ? `YouTube ${track.kind} (${track.lang}) · yt-dlp timing fallback` : 'YouTube captions · yt-dlp timing fallback'
          };
        }
      } catch {}
    }
    return null;
  } catch (error) {
    if (isAbort(error)) throw error;
    console.warn('[youtube lyrics] subtitle materialization fallback unavailable:', String(error?.message || error).replace(/\s+/g, ' ').slice(0, 220));
    return null;
  } finally {
    try { fs.rmSync(dir, { recursive: true, force: true }); } catch {}
  }
}

async function getYouTubeLyrics(song, ytdlpPath, execFileAsync, fetchImpl = fetch, options = {}) {
  if (!song?.url || !ytdlpPath) return null;
  try {
    const { stdout } = await runYtDlpSmart(
      ytdlpPath,
      ['--no-warnings', '--no-playlist', '--skip-download', '-J', song.url],
      {
        maxBuffer: 20 * 1024 * 1024,
        timeout: 30000,
        signal: options.signal,
        purpose: 'lyrics-metadata',
        browserAuth: options.browserAuth,
        allowFallbackClients: true
      }
    );
    const info = JSON.parse(stdout);
    const metadata = {
      title: info.track || info.alt_title || info.title || song.title || '',
      artist: info.artist || info.creator || info.album_artist || '',
      language: info.language || info.original_language || '',
      originalLanguage: info.original_language || info.language || '',
      videoTitle: info.title || song.title || ''
    };
    const track = chooseCaptionTrack(info);
    if (options.metadataOnly) {
      return { metadata, captionLanguage: track?.lang || metadata.originalLanguage || metadata.language || '' };
    }
    let rows = [], wordTimings = [], source = '';

    // Fast path: fetch the subtitle URL yt-dlp already resolved. Some YouTube sessions
    // reject that CDN URL even though metadata/playback succeeded, so failure here is
    // no longer treated as "no sync"; a yt-dlp subtitle materialization fallback follows.
    if (track) {
      try {
        const timeoutSignal = AbortSignal.timeout(7000);
        const fetchSignal = options.signal && typeof AbortSignal.any === 'function' ? AbortSignal.any([options.signal, timeoutSignal]) : timeoutSignal;
        const response = await fetchImpl(track.url, { signal: fetchSignal, headers: { 'User-Agent': 'Deepeccho/2.2.7' } });
        if (response.ok) {
          const captionOptions = { rolling: track.kind === 'automatic captions' };
          if (track.ext === 'json3') { const parsed = parseJson3Detailed(await response.json(), captionOptions); rows = parsed.rows; wordTimings = parsed.wordTimings; }
          else { const parsed = parseVttDetailed(await response.text(), captionOptions); rows = parsed.rows; wordTimings = parsed.wordTimings; }
          if (rows.length) source = `YouTube ${track.kind} (${track.lang})`;
        }
      } catch (error) {
        if (isAbort(error)) throw error;
      }
    }

    if (!rows.length) {
      const fallback = await materializeCaptionFallback(song, ytdlpPath, metadata, track, options);
      if (fallback?.rows?.length) {
        rows = fallback.rows;
        wordTimings = fallback.wordTimings || [];
        source = fallback.source;
      }
    }

    if (!rows.length) return { metadata };
    return {
      syncedLyrics: rowsToLrc(rows),
      source: source || 'YouTube captions',
      captionLanguage: track?.lang || metadata.originalLanguage || metadata.language || '',
      metadata,
      wordTimings,
      wordTimingSource: wordTimings.length ? `${source || 'YouTube captions'} segment timing` : ''
    };
  } catch (error) {
    if (isAbort(error)) return { cancelled: true };
    if (error?.code === 'YOUTUBE_BOT_CHECK') {
      console.warn('[youtube lyrics] YouTube bot-check; captions skipped for this request.');
      return { blocked: true, errorCode: 'YOUTUBE_BOT_CHECK' };
    }
    console.warn('[youtube lyrics] fallback unavailable:', String(error?.message || error).slice(0, 240));
    return null;
  }
}

module.exports = { cleanCaptionText, collapseCaptionLines, parseJson3, parseJson3Detailed, parseVtt, parseVttDetailed, normalizeWordTimings, rowsToLrc, chooseCaptionTrack, subtitleLanguageCandidates, parseCaptionFile, captionFileScore, materializeCaptionFallback, alignSyncedLyricsToCaptions, alignSyncedLyricsToCaptionsStable, getYouTubeLyrics, normalizeLang };
