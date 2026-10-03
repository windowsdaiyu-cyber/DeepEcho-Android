'use strict';
const assert = require('assert');
const { mapEntry, durationText, parseLrc, candidateExecutables } = require('./server');
assert.strictEqual(durationText(125), '2:05');
assert.strictEqual(mapEntry({id:'abcdefghijk',title:'T',channel:'A',duration:60}).durationMs, 60000);
assert.strictEqual(mapEntry({id:'bad id',title:'T'}), null);
assert.deepStrictEqual(parseLrc('[00:01.50]Hello\n[01:02.00]World'), [{startMs:1500,text:'Hello'},{startMs:62000,text:'World'}]);
assert.ok(Array.isArray(candidateExecutables()));
console.log('bridge tests: PASS');
