(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.DeepecchoLyricsTimeline = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  function textKey(value = '') {
    return String(value || '').normalize('NFKC').toLocaleLowerCase()
      .replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
  }

  function normalizeTimeline(rows = [], options = {}) {
    const dedupeWindow = Number.isFinite(Number(options.dedupeWindow)) ? Math.max(0, Number(options.dedupeWindow)) : 0.45;
    const groupWindow = Number.isFinite(Number(options.groupWindow)) ? Math.max(0.02, Number(options.groupWindow)) : 0.24;
    const endPadding = Number.isFinite(Number(options.endPadding)) ? Math.max(0.02, Number(options.endPadding)) : 0.10;
    const tailStep = Number.isFinite(Number(options.tailStep)) ? Math.max(0.35, Number(options.tailStep)) : 1.35;

    const sorted = rows
      .map((row, order) => ({ time: Number(row?.time), text: String(row?.text || '').trim(), order }))
      .filter(row => Number.isFinite(row.time) && row.time >= 0 && row.text)
      .sort((a, b) => (a.time - b.time) || (a.order - b.order));

    // Final lyric timelines must not use the auto-caption "progressive text" heuristic.
    // A real lyric can legitimately be "when you go" followed by "when you go home".
    // Only remove a truly identical near-time duplicate here.
    const deduped = [];
    for (const row of sorted) {
      const key = textKey(row.text);
      if (!key) continue;
      const prev = deduped[deduped.length - 1];
      if (prev && key === textKey(prev.text) && (row.time - prev.time) >= 0 && (row.time - prev.time) <= dedupeWindow) continue;
      deduped.push({ time: row.time, text: row.text });
    }

    // Some LRC/caption providers attach several distinct lyric lines to exactly the same
    // cue time (or alignment code leaves them ~60 ms apart). Do not flash through those
    // lines in a few milliseconds. Spread only that near-simultaneous cluster inside the
    // time available before the next real cue, preserving every line and its order.
    const out = deduped.map(row => ({ ...row }));
    let i = 0;
    while (i < out.length) {
      const start = i;
      const startTime = out[start].time;
      let end = start + 1;
      while (end < out.length && (out[end].time - startTime) <= groupWindow) end++;
      const count = end - start;
      if (count > 1) {
        const nextTime = end < out.length ? out[end].time : NaN;
        let step = tailStep;
        if (Number.isFinite(nextTime) && nextTime > startTime) {
          const available = Math.max(0.08 * count, nextTime - startTime - endPadding);
          step = Math.max(0.08, Math.min(2.6, available / count));
        }
        for (let k = 0; k < count; k++) out[start + k].time = startTime + step * k;
      }
      i = end;
    }

    // Last defensive monotonic pass. This is normally a no-op, but protects binary search
    // from malformed provider rows without creating the old 55 ms rapid-fire sequence.
    for (let n = 1; n < out.length; n++) {
      if (out[n].time <= out[n - 1].time) out[n].time = out[n - 1].time + 0.08;
    }
    return out;
  }

  function findActiveIndex(lines = [], playbackTime = 0) {
    let lo = 0, hi = lines.length - 1, answer = -1;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      if (Number(lines[mid]?.time) <= playbackTime) { answer = mid; lo = mid + 1; }
      else hi = mid - 1;
    }
    return answer;
  }

  return { textKey, normalizeTimeline, findActiveIndex };
});
